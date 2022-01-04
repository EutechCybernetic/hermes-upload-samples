# Uploading files with Java

This sample has been tested with `javac 11 & java 1.8.0_241`.

## Get the code

```text
git clone <this-repo>
cd <this-repo>/Java/ResumableUpload
```

## Build

```text
# this creates directory called "bin" & places all class files
javac -d bin --release 8 --source-path src src/com/ResumableUpload/ResumableUpload.java
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <http://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `java -cp bin com.ResumableUpload.ResumableUpload` for usage reference.

```text
java -cp bin com.ResumableUpload.ResumableUpload upload <apikey> <iviva-upload-url> <path-to-file>

java -cp bin com.ResumableUpload.ResumableUpload upload SC:testaccount:123456 http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2 /path/to/Downloads/file.txt
```

## Eclipse Way

If you are tired of the above process, simply import this project into `Eclipse` and provide the CLI arguments in Eclipse's `Run Configuration` option. More than that, you will have to checkout the web on how to do that in Eclipse.

Tested with `Eclipse 2020-03 (4.15.0)`.
