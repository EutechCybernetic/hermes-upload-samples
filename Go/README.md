# Uploading files with C-Sharp

This sample has been tested with `go 1.9.4`.

## Get the code

```text
git clone <this-repo>
cd <this-repo>/go/resumable-upload
go build
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <https://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <https://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `./resumable-upload --help` for usage reference.

```text
./resumable-upload upload <apikey> <iviva-upload-url> <path-to-file>

./resumable-upload upload SC:testaccount:123456 https://testaccount.ivivacloud.com/filemanager/folder1/folder2 ~/Downloads/file.txt
````
