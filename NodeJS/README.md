# Uploading files with NodeJS

This sample has been tested with `Node v12.3.1` & `npm 6.9.0`.

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

Use `node app.js --help` or `node app.js upload --help` for usage reference.

```text
node app.js upload <apikey> <iviva-upload-url> <path-to-file>

node app.js upload SC:testaccount:123456 https://testaccount.ivivacloud.com/filemanager/folder1/folder2 ~/Downloads/file.txt
````
