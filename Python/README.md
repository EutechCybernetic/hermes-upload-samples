# Uploading files with Python

This sample has been tested with `Python 2.7.13`.

## Get the code

```text
git clone <this-repo>
cd <this-repo>/Python
pip install -r requirements.txt
```

## iviva server upload URL

``` text
https://<server>/filemanager/upload/<path>
```

### Example

* <http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <http://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `python app.py --help` or `python app.py upload --help` for usage reference.

```text
python app.py upload <apikey> <iviva-upload-url> <path-to-file>

python app.py upload SC:testaccount:123456 http://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2 /path/to/Downloads/file.txt
````
