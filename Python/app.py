import sys
import io
import os
import argparse
import colorama
import requests
import uuid

MB = 1 * 1024 * 1024
CHUNK_SIZE = 5 * MB

class FileNotFoundException(Exception):
  pass

class APIException(Exception):
  pass

def parse_args():
  parser = argparse.ArgumentParser()
  sub_parsers = parser.add_subparsers()

  upload_parser = sub_parsers.add_parser("upload", help = "upload file to remote server")
  upload_parser.add_argument("apikey", help = "Authorization for remote server URL")
  upload_parser.add_argument("url", help = "Remote server URL for upload")
  upload_parser.add_argument("file", help = "File to upload")
  
  return parser.parse_args()

def cprint(text, color):
  """ Prints the given text, formatted with given color

  Parameters
  ----------
  name : text
      Text to be printed to console
  name: qs
      Color format
  """

  print(color + text)

def add_qs_to_url(url, qs):
  """ Adds given query params to target url

  Parameters
  ----------
  name : url
      Target URL to append query params toz
  name: qs
      Query params to be appended

  Returns
  -------
  string
      URL string with query params
  """

  result = url + "?" if url.find("?") == - 1 else url
  qs_array = []
  
  for key in qs.keys():
    qs_array.append("{0}={1}".format(key, qs[key]))

  return result + "&".join(qs_array)

def upload_chunk(url, filename, apikey, data):
  """ Uploads current chunk to the server

  Parameters
  ----------
  name : url
      Target URL for upload
  name: filename
      Filename to be used in multipart file upload
  name: apikey
      Apikey to be passed in Authorization header
  name: data
      Chunk to be uploaded

  Returns
  -------
  string
      Server response

  Raises
  ------
  APIException
      If the server returns non-200 response code
  """

  with io.BytesIO(data) as buffer:
    headers = {
      "Authorization": apikey
    }
    files = {
      "file": (filename, buffer, "application/octet-stream")
    }

    response = requests.post(url, headers = headers, files = files)
    response_text = response.text

    # something went wrong, no point proceeding
    if response.status_code != 200:
      raise APIException(response_text)

    return response_text

def upload(args):   
  """ Uploads a given file in chunks to the server

  Parameters
  ----------
  name : args 
      Arguments passed to the program

  Raises
  ------
  APIException
      If the server returns non-200 response code
  FileNotFoundException
      If the given file doesn't exist or is not a file
  """

  apikey = args.apikey
  url = args.url
  target_file = os.path.expanduser(args.file)

  try:
    if not (os.path.exists(target_file) and os.path.isfile(target_file)):
      raise FileNotFoundException("File doesn't exist - {0}".format(target_file))

    with open(target_file, "rb") as file_handle:
      resumable_total_size = os.path.getsize(target_file)
      resumable_file_name = os.path.basename(target_file)
      resumable_identifier = "{0}-{1}".format(resumable_total_size, resumable_file_name)
      resumable_chunks = resumable_total_chunks = (resumable_total_size // CHUNK_SIZE) + 1
      resumable_chunk_size = CHUNK_SIZE
      upload_token = str(uuid.uuid4())

      for i in range(1, resumable_chunks + 1):
        target_url = add_qs_to_url(url, {
          "resumableChunkNumber": i,
          "resumableFilename": resumable_file_name,
          "uploadToken": upload_token
        })

        response = requests.get(target_url, headers = {
          "Authorization": apikey
        })

        # server already has the chunk, proceed to next
        if response.status_code == 200:
          cprint("[{0}/{1}] Chunks exists!".format(i, resumable_chunks), colorama.Fore.GREEN)
        # we are good to read the next chunk and upload to server
        elif response.status_code == 404:
          data = file_handle.read(CHUNK_SIZE)

          target_url = add_qs_to_url(url, {
            "resumableChunkNumber": i,
            "resumableFilename": resumable_file_name,
            "resumableChunkSize": resumable_chunk_size,
            "resumableTotalSize": resumable_total_size,
            "resumableIdentifier": resumable_identifier,
            "resumableTotalChunks": resumable_total_chunks,
            "uploadToken": upload_token
          })

          cprint("[{0}/{1}] Uploading chunk of size {2} bytes".format(i, resumable_chunks, len(data)), colorama.Fore.GREEN)

          response_text = upload_chunk(target_url, resumable_file_name, apikey, data)

          # last chunk's upload response has the "File Reference" we neeed pass to "Lucy" action 
          if i == resumable_chunks:
            print("Result:")
            cprint(response_text, colorama.Fore.GREEN)

            sys.exit(0)
        # something went wrong, no point proceeding
        else:
          raise APIException(response.text)
  except Exception as err:
    cprint(str(err), colorama.Fore.RED)

    sys.exit(1)
  
if __name__ == "__main__":
  colorama.init()

  args = parse_args()

  upload(args)