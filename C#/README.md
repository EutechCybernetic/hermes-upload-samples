# Uploading files with C-Sharp

This sample has been tested with `dotnet 2.2.110` & `c# v7.1`.

## Get the code

```text
git clone <this-repo>
cd <this-repo>/C#/ResumableUpload
dotnet build
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <http://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `dotnet run -- --help` for usage reference.

```text
dotnet run upload <apikey> <iviva-upload-url> <path-to-file>

dotnet run upload SC:testaccount:123456 http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2 /path/to/Downloads/file.txt
````
