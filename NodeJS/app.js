const fsPromises = require("fs").promises;
const path = require("path");
const fetch = require("node-fetch");
const yargs = require("yargs");
const chalk = require("chalk");
const FormData = require('form-data');

const MB = 1 * 1024 * 1024;
const CHUNK_SIZE = 5 * MB;

let argv = yargs
  .command("upload [apikey] [url] [file]", "upload file to remote server", y => {
    y.positional("apikey", {
      describe: "Authorization for remote server URL",
    })
    .positional("url", {
      describe: "Remote server URL for upload",
    })
    .positional("file", {
      describe: "File to upload"
    });
  }).argv;

/**
 * 
 * @param {*} arg Argument name to check from command line arguments
 */
function checkArgExists(arg) {
  if(!argv[arg]) {
    yargs.showHelp();

    console.error(`\nargument ${chalk.redBright(arg)} is required`);

    process.exit(1);
  }
}

/**
 * 
 * @param {*} args List of argument names to check from command line arguments
 */
function validateArgs(args) {
  if(!!args && args.length > 0) {
    args.forEach(arg => checkArgExists(arg))
  } else {
    console.warn(chalk.yellowBright("No arguments to validate"));
  }
}

/**
 * Adds given query params to target server
 * 
 * @param {*} url Target URL to append query params to
 * @param {*} qs Query params to be appended
 * @returns URL with query params appended
 */
function addQSToURL(url, qs) {
  let result = url.lastIndexOf("?") === -1 ? url + "?" : url;
  let qsArray = [];

  if(!!url && !!qs) {
    for(let key in qs) {
      qsArray.push(`${key}=${qs[key]}`);
    }
  }

  return result + qsArray.join("&");
}

/**
 * Uploads current chunk to server
 * 
 * @param {*} url Target URL for upload
 * @param {*} filename Filename to be used in multipart file upload
 * @param {*} apikey Apikey to be passed in Authorization header
 * @param {*} buffer Chunk to be uploaded
 */
async function uploadChunk(url, filename, apikey, buffer) {
  const form = new FormData();
  form.append('file', buffer, {
    filename,
    contentType: "application/octet-stream"
  });

  let response = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `${apikey}`,
      ...form.getHeaders()
    },
    body: form
  });

  let responseText = await response.text();

  // something went wrong, no point proceeding
  if(response.status !== 200) {
    console.error(chalk.redBright(responseText));

    process.exit(1);
  }

  return responseText;
}

/**
 * Begin uploading the given file in chunks to server
 */
async function upload() {
  validateArgs(["apikey", "url", "file"]);

  const file = argv.file;
  const apikey = argv.apikey;
  const baseUrl = argv.url;
  let fileHandle = null;

  try {
    fileHandle = await fsPromises.open(file);
    let fileStats = await fileHandle.stat();

    if(!fileStats.isFile()) {
      console.error(chalk.redBright(`File doesn't exist - ${file}`));

      process.exit(1);
    }

    const buffer = new Uint8Array(CHUNK_SIZE);
    const resumableTotalSize = fileStats.size;
    const resumableFilename = path.basename(file);
    const resumableIdentifier = `${resumableTotalSize}-${resumableFilename}`;
    const resumableChunks = Math.floor(resumableTotalSize / CHUNK_SIZE) + 1;
    const resumableChunkSize = CHUNK_SIZE;
    
    for(let i = 1; i <= resumableChunks; i++) {
      let url = addQSToURL(baseUrl, {
        resumableChunkNumber: i,
        resumableFilename,
        resumableChunkSize,
        resumableTotalSize,
        resumableIdentifier,
      });

      let response = await fetch(url, {
        method: "GET",
        headers: {
          "Authorization": `${apikey}`
        }
      });

      // server already has the chunk, proceed to next
      if(response.status === 200) {
        console.log(chalk.greenBright(`[${i}/${resumableChunks}] Chunk exists!`));
      }
      // we are good to read the next chunk and upload to server
      else if(response.status === 400) {
        let readResult = await fileHandle.read(buffer, 0, CHUNK_SIZE);
        
        // bytes read can be lesser than buffer size
        // so we only upload that
        let targetBuffer = Buffer.from(buffer);
        targetBuffer = targetBuffer.slice(0, readResult.bytesRead);

        console.log(chalk.greenBright(`[${i}/${resumableChunks}] Uploading chunk of size ${targetBuffer.length} bytes`));

        let responseText = await uploadChunk(url, resumableFilename, apikey, targetBuffer);

        // last chunk's upload response has the "File Reference" we neeed pass to "Lucy" action 
        if(i === resumableChunks) {
          console.log(`Result:\n${chalk.greenBright(responseText)}`);
          
          process.exit(0);
        }
      }
      // something went wrong, no point proceeding
      else {
        console.error(chalk.redBright(await response.text()));

        process.exit(1);
      }

      console.log();
    }
  } catch (error) {
    console.error(chalk.redBright(error));

    process.exit(1);
  } finally {
    if(!!fileHandle && !!fileHandle.close) {
      fileHandle.close();
    }
  }
}

upload();