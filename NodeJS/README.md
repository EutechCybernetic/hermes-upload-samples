# Uploading files with NodeJS

## Get the code

```text
git clone <this-repo>
cd <this-repo>/NodeJS
npm install
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <https://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <https://testaccount.ivivacloud.com/filemanager/upload>

## Usage

`node app.js <apikey> <iviva-upload-url> <path-to-file>`

Example: `node app.js SC:testaccount:123456 https://testaccount.ivivacloud.com/filemanager/folder1/folder2 ~/Downloads/file.txt`
