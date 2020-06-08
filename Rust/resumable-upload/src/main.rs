mod args;
mod log;
mod errors;

use std::env;
use std::process;
use std::path::Path;
use std::io::{Read};
use std::fs::{self, File};
use std::collections::HashMap;
use reqwest::{self, blocking::Client, Url};
use args::Arguments;
use log::Log;
use errors::UploadError;

fn print_usage() {
    println!("Resumable Upload");
    println!("----------------");
    println!("Usage:\n\tupload [apikey] [url] [file]");
}

fn parse_args(args: env::Args) -> Result<Arguments, String> {
    if args.len() > 1 {
        let args: Vec<String> = args
            .skip(1)
            .collect();

        if args.get(0).unwrap().to_lowercase()  == "upload" {
            let args: Vec<String> = args
                .iter()
                .skip(1)
                .map(|item| item.clone())
                .collect();

            if args.len() < 3 {
                return Err("Too few arguments".to_string());
            }

            return Ok(Arguments {
                apikey: args.get(0).unwrap().clone(),
                url: args.get(1).unwrap().clone(),
                file: args.get(2).unwrap().clone(),
            });
        }
        else if args.get(0).unwrap().to_lowercase() == "--help" {
            return Err("".to_string());
        }
        else {
            return Err(format!("Invalid command: {}", args.get(0).unwrap()));
        }
    }

    Err("".to_string())
}

/// Returns url with query params added
///
/// # Arguments
///
/// * `url` - Target URL to append query params to
/// * `qs` - Query params to be appended
fn add_qs_to_url(url: &str, qs: &HashMap<String, String>) -> String {
    let mut url = format!("{}", url);

    if let None = url.rfind('?') {
        url = format!("{}?", url);
    }

    let mut qs_array: Vec<String> = vec![];

    for (key, value) in qs {
        qs_array.push(format!("{}={}", key, value));
    }

    url = format!("{}{}", url, qs_array.join("&"));

    return url;
}

/// Uploads current chunk to the server
///
/// # Arguments
///
/// * `url` - Target URL for upload
/// * `filename` - Filename to be used in multipart file upload
/// * `apikey` - Apikey to be passed in Authorization header
/// * `data` - Chunk to be uploaded
/// 
/// # Returns
///
/// server response if status code is 200, else error
fn upload_chunk(client: &Client, url: &str, filename: &str, apikey: &str, data: Vec<u8>) -> Result<String, Box<dyn std::error::Error>> {
    let part = reqwest::blocking::multipart::Part::bytes(data)
        .file_name(filename.to_string())
        .mime_str("application/octet-stream")?;
    let form = reqwest::blocking::multipart::Form::new()
        .part("file", part);
    let response = client.post(Url::parse(&url)?)
        .header("Authorization", apikey)
        .multipart(form)
        .send()?;
    let status_code = response.status();
    let content = response.text()?;

    if status_code != 200 {
        return Err(Box::new(UploadError::new(content)));
    }

    Ok(content)
}

/// Uploads a given file in chunks to the server
///
/// # Arguments
///
/// * `args` - Arguments passed to the program
#[allow(unused)]
fn upload(args: Arguments) -> Result<(), Box<dyn std::error::Error>> {

    let apikey = args.apikey;
    let url = args.url;
    let target_file = args.file;
    let file_path = Path::new(&target_file);
    let mut file = File::open(&target_file)?;
    let metadata = fs::metadata(&target_file)?;
    let resumable_filename = file_path.file_name()
        .unwrap()
        .to_os_string()
        .into_string()
        .unwrap();
    let resumable_total_size = metadata.len();
    let resumable_identifier = format!("{}-{}", resumable_total_size, resumable_filename);
    let resumable_chunk_size = 5 * 1024 * 1024;
    let resumable_chunks = (resumable_total_size / resumable_chunk_size) + 1;
    let client = Client::new();

    for i in 1..=resumable_chunks {
        let mut qs: HashMap<String, String> = HashMap::new();
        qs.insert(String::from("resumableChunkNumber"), format!("{}", i));
        qs.insert(String::from("resumableFilename"), format!("{}", resumable_filename));
        qs.insert(String::from("resumableIdentifier"), format!("{}", resumable_identifier));

        let target_url = add_qs_to_url(&url, &qs);

        let response = client.get(Url::parse(&target_url)?)
            .header("Authorization", format!("{}", apikey))
            .send()?;
        let status_code = response.status();
        let content = response.text()?;

        // server already has the chunk, proceed to next
        if status_code == 200 {
            Log::print_ok(format!("[{}/{}] Chunk exists!", i, resumable_chunks));
        }
        // we are good to read the next chunk and upload to server
        else if status_code == 400 {
            let mut qs: HashMap<String, String> = HashMap::new();
            qs.insert(String::from("resumableChunkNumber"), format!("{}", i));
            qs.insert(String::from("resumableFilename"), format!("{}", resumable_filename));
            qs.insert(String::from("resumableChunkSize"), format!("{}", resumable_chunk_size));
            qs.insert(String::from("resumableTotalSize"), format!("{}", resumable_total_size));
            qs.insert(String::from("resumableIdentifier"), format!("{}", resumable_identifier));

            let target_url = add_qs_to_url(&url, &qs);
            let mut buffer: Vec<u8> = vec![0; resumable_chunk_size as usize];

            let bytes_read = file.read(&mut buffer)?;
            
            // bytes read can be lesser than chunk size, so we take only that
            buffer.resize_with(bytes_read as usize, std::default::Default::default);

            Log::print_ok(format!("[{}/{}] Uploading chunk of size {} bytes", i, resumable_chunks, bytes_read));

            let response_text = upload_chunk(&client, &target_url, 
                &resumable_filename, &apikey, buffer)?;

            // last chunk's upload response has the "File Reference" we neeed pass to "Lucy" action 
            if i == resumable_chunks {
                println!("Result:");
                Log::print_ok(format!("{}", response_text));
            }
        }
        // something went wrong, no point proceeding
        else {
            return Err(Box::new(UploadError::new(content)));
        }
    }

    Ok(())
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = env::args();
    let args = parse_args(args);

    match args {
        Ok(args) => Ok(upload(args)?),
        Err(err) => {
            print_usage();

            if !err.is_empty() {
                Log::print_error(format!("Error: {}", err));

                process::exit(1);
            }

            process::exit(0);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn adds_qs_to_url_without_qsmark() {
        let url = String::from("http://localhost:8080");
        let mut qs: HashMap<String, String> = HashMap::new();

        qs.insert(String::from("a"), String::from("1"));

        let url = add_qs_to_url(&url, &qs);

        assert_eq!(url, String::from("http://localhost:8080?a=1"));
    }

    #[test]
    fn adds_qs_to_url_with_qsmark() {
        let url = String::from("http://localhost:8080?");
        let mut qs: HashMap<String, String> = HashMap::new();

        qs.insert(String::from("a"), String::from("1"));

        let url = add_qs_to_url(&url, &qs);

        assert_eq!(url, String::from("http://localhost:8080?a=1"));
    }
}