package com.ResumableUpload;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResumableUpload {
	final static int MB = 1 * 1024 * 1024;
    final static int CHUNK_SIZE = 5 * MB;
    final static int BUFFER_SIZE = MB;
    
	static void printUsage() {
		System.out.println("ResumableUpload");
		System.out.println("===============");
		System.out.println("Usage:\n\tupload [apikey] [url] [file]");
	}
	
	static Arguments parseArgs(String[] args) throws IllegalArgumentException {
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("upload")) {
				if (args.length < 4) {
					throw new IllegalArgumentException("Too few arguments");
				}
				
				Arguments arguments = new Arguments();
				arguments.ApiKey = args[1];
				arguments.URL = args[2];
				arguments.File = args[3];
				
				return arguments;
			}
			else if (args[0].equalsIgnoreCase("--help")) {
				printUsage();
				
				System.exit(0);
			}
			else {
				throw new IllegalArgumentException("Invalid command: " + args[0]);
			}
		}
		else {
			printUsage();
			
			System.exit(0);
		}
		
		return null;
	}
	
	/**
	 * Adds given query params to target url
	 * @param url Target URL to append query params to
	 * @param qs Query params to be appended
	 * @return URL string with query params
	 */
	static String addQsToUrl(String url, Map<String, Object> qs) {
		String result = url.lastIndexOf("?") == -1 ? url + "?" : url;
		List<String> qsArray = new ArrayList<String>();

		for(String key: qs.keySet()) {
			qsArray.add(String.format("%s=%s", key, qs.get(key)));
		}
		
		return result + String.join("&", qsArray);
	}
	
	/**
	 * Extracts response text from HTTP response
	 * @param connection HTTP connection
	 * @return HTTP response content
	 * @throws IOException
	 */
	static String ExtractContentFromResponse(HttpURLConnection connection) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		
		in.close();

		return response.toString();
	}
	
	public static void main(String[] args) {
		try {
			Arguments arguments = parseArgs(args);
			
			upload(arguments);
		} catch(IllegalArgumentException argExp) {
			printUsage();
			
			Log.printError("%s", argExp.getMessage());
			
			System.exit(1);
		} catch (Exception exp) {
			Log.printError("%s", exp.getMessage());
			
			System.exit(1);
		}
	}
	
	/**
	 * Uploads current chunk to the server
	 * @param url Target URL for upload
	 * @param filename Filename to be used in multipart file upload
	 * @param apikey Apikey to be passed in Authorization header
	 * @param data Chunk to be uploaded
	 * @return HTTP response content
	 * @throws IOException
	 */
	static String uploadChunk(String url, String filename, String apikey, byte[] data) throws IOException {
		String attachmentName = "file";
		String attachmentFilename = filename;
		String crlf = "\r\n";
		String twoHyphens = "--";
		String boundary = UUID.randomUUID().toString();
		DataOutputStream requestOutputStream = null;
		URL client = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) client.openConnection();
		
		try {
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Authorization", apikey);
			connection.setRequestProperty("Content-Type", 
					String.format("multipart/form-data;boundary=\"%s\"", boundary));
			
			requestOutputStream = new DataOutputStream(connection.getOutputStream());
			
			requestOutputStream.writeBytes(twoHyphens + boundary);
			requestOutputStream.writeBytes(crlf);
			
			requestOutputStream.writeBytes(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"", 
					attachmentName, attachmentFilename));
			requestOutputStream.writeBytes(crlf);
			
			requestOutputStream.writeBytes("Content-Type: application/octet-stream");
			requestOutputStream.writeBytes(crlf);
			requestOutputStream.writeBytes(crlf);
			
			requestOutputStream.write(data);
			requestOutputStream.writeBytes(crlf);
			
			requestOutputStream.writeBytes(twoHyphens + boundary + twoHyphens);
			requestOutputStream.writeBytes(crlf);
			
			requestOutputStream.flush();
			requestOutputStream.close();
			
			int responseCode = connection.getResponseCode();
			String responseText = ExtractContentFromResponse(connection);
			
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException(responseText);
			}
			
			return responseText;
		} finally {
			if (requestOutputStream != null) {
				requestOutputStream.close();
			}
		}
	}
	
	/**
	 * Uploads a given file in chunks to the server
	 * @param args Arguments passed to the program
	 * @throws IOException
	 */
	static void upload(Arguments args) throws IOException {
		String apikey = args.ApiKey;
		String url = args.URL;
		String targetFile = args.File;
		
		File file = new File(targetFile);
		
		if(!file.exists() || !file.isFile()) {
			throw new FileNotFoundException("File doesn't exist: " + targetFile);
		}
		
		BufferedInputStream fs = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
		
		try {
			long resumableTotalSize = file.length();
			String resumableFilename = file.getName();
			String resumableIdentifier = String.format("%s-%s", resumableTotalSize, resumableFilename);
			int resumableChunks = (int)(resumableTotalSize / CHUNK_SIZE) + 1;
			int resumableChunkSize = CHUNK_SIZE;
			int resumableTotalChunks = resumableChunks;
			String uploadToken = UUID.randomUUID().toString();
			
			for(int i = 1; i <= resumableChunks; i++) {
				Map<String, Object> qs = new HashMap<String, Object>();
				qs.put("resumableChunkNumber", i);
				qs.put("resumableFilename", resumableFilename);
				qs.put("uploadToken", uploadToken);
				
				String targetUrl = addQsToUrl(url, qs);
				
				URL client = new URL(targetUrl);
				HttpURLConnection connection = (HttpURLConnection) client.openConnection();
				connection.setRequestMethod("GET");
				connection.setRequestProperty("Authorization", apikey);
				
				int responseCode = connection.getResponseCode();
				
				// server already has the chunk, proceed to next
				if (responseCode == HttpURLConnection.HTTP_OK) {
					Log.printOK("[%s/%s] Chunk exists!", i, resumableChunks);
				}
				// we are good to read the next chunk and upload to server
				else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
					qs = new HashMap<String, Object>();
					qs.put("resumableChunkNumber", i);
					qs.put("resumableFilename", resumableFilename);
					qs.put("resumableChunkSize", resumableChunkSize);
					qs.put("resumableTotalSize", resumableTotalSize);
					qs.put("resumableIdentifier", resumableIdentifier);
					qs.put("resumableTotalChunks", resumableTotalChunks);
					qs.put("uploadToken", uploadToken);
					
					targetUrl = addQsToUrl(url, qs);

					byte[] buffer = new byte[CHUNK_SIZE];
					
					int bytesRead = fs.read(buffer, 0, CHUNK_SIZE);
					
					Log.printOK("[%s/%s] Uploading chunk of size %s bytes", i, resumableChunks, bytesRead);
					
					// bytes read can be lesser than chunk size, so we take only that
					String responseText = uploadChunk(targetUrl, resumableFilename, apikey, Arrays.copyOf(buffer, bytesRead));
					
					if (i == resumableChunks) {
						System.out.println("Result:");
						Log.printOK("%s", responseText);
					}
				}
				// something went wrong, no point proceeding
				else {
					throw new IOException(ExtractContentFromResponse(connection));
				}
			}
		} finally {
			fs.close();
		}
	}
}
