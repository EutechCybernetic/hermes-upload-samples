# Uploading files with NodeJS

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

* <https://testaccount.ivivacloud.com/filemanager/upload/folder1/folder2>
* <https://testaccount.ivivacloud.com/filemanager/upload>

## Usage

Use `python app.py --help` or `python app.py upload --help` for usage reference.

```text
python app.py upload <apikey> <iviva-upload-url> <path-to-file>

python app.py upload SC:testaccount:123456 https://testaccount.ivivacloud.com/filemanager/folder1/folder2 ~/Downloads/file.txt
````
