# Uploading files with C-Sharp

This sample has been tested with `rustc 1.40.0 (73528e339 2019-12-16)` & `cargo 1.40.0 (bc8e4c8be 2019-11-22)`.

## Get the code

```text
git clone <this-repo>
cd <this-repo>/Rust/resumable-upload
cargo build
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <https://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <https://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `cargo run -- --help` for usage reference.

```text
cargo run upload <apikey> <iviva-upload-url> <path-to-file>

cargo run upload SC:testaccount:123456 https://testaccount.ivivacloud.com/filemanager/folder1/folder2 ~/Downloads/file.txt
````
