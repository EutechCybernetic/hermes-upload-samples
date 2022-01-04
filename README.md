# Hermes Upload Samples

This repository is aimed at providing a starter code in several languages for uploading large files to an `iviva` web server with [ResumableJS](http://www.resumablejs.com/) style uploads.

**Note:** From here on, `ResumableJS` will be referred to as `resumable` in the rest of the document.

## iviva server upload URL

```text
https://<server>/filemanager/upload/<path>
```

### Example

* <http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <http://testaccount.ivivacloud.com/filemanager/upload>

## Basic resumable mechanism

The basic idea behind resumable upload is, to split a file into multiple chunks, based on client defined `chunk size`. Then upload one chunk at time and the server is then responsible to stitching the files into one. This lifts the pressure from having to load entire file into memory while uploading.

Let's see the minimal steps involved in ResumableJS style upload,

1. To upload a chunk, issue a `GET` request to the `iviva` server upload URL with the following `query string` parameters,
    1. `resumableChunkNumber` - current chunk number you are trying to upload
    2. `uploadToken` - a random text like `UUID V4`
2. If the server responds with status `200`, skip current chunk & proceed to upload the next chunk as in `Step 1`, else move to `Step 3`
3. If the server response with status `404`, issue a `POST` request to `iviva` server upload URL with the file passed as `multipart form-data` and the following `query string` parameters,
    1. `resumableChunkNumber` - current chunk number you are trying to upload
    2. `resumableFilename` - actual file name
    3. `resumableChunkSize` - size of the current chunk being uploaded
    4. `resumableTotalSize` - file size
    5. `resumableIdentifier` - a unique ID for the file, Example: `<filesize>-<filename>`
    6. `resumableTotalChunks` - total number of chunks that will be uploaded from the client
    7. `uploadToken` - a random text like `UUID V4` (**Note:** should be the same as the one sent during `GET` request)
4. If the server responds with a status `200`, repeat `Steps 1-4`, else retry
5. On uploading the final chunk, the server returns a `File Reference` in json format

    ```json
    {
      "Name": "file.txt",
      "Reference": "/folder1/folder2/file.txt",
      "Provider": 6,
      "ContentLength": 21228931,
      "Metadata":
        {
          "apikey": "SC:netcore:a7c35a7060b27805",
          "Account": "netcore"
        }
    }
    ```

6. Pass the above json string in the request to your `Lucy` action, in order to copy the file to different destinations like `Google Drive`, `AWS S3`, `Dropbox` or even to a `local filesystem`
  
## How to upload my file in a specific folder format

By default, if `<path>` is empty, the file will be uploaded directly to the root of a random folder. To upload it in a specific folder structure, like `folder1 -> folder2 -> file.txt`, your serve upload url would be - `http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2`

**Note:** You don&#39;t have to pass the file name along with the path, since its already passed in `query string` parameters, while you upload the file.

## Starter code is available for

1. NodeJS
2. Python
3. C#
4. Java
5. Rust
6. Go
