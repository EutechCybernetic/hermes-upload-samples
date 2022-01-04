package main

import (
	"bytes"
	"errors"
	"fmt"
	"io/ioutil"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"os"
	"strings"

	"github.com/fatih/color"
	"github.com/nu7hatch/gouuid"
)

const (
	MB         = 1 * 1024 * 1024
	CHUNK_SIZE = 5 * MB
)

type argument struct {
	APIKey string
	URL    string
	File   string
}

func printError(format string, args ...interface{}) {
	color.Red(format, args...)
}

func printOK(format string, args ...interface{}) {
	color.Green(format, args...)
}

func printUsage() {
	fmt.Println("Resumable Upload")
	fmt.Println("================")
	fmt.Println("Usage\n\tupload [apikey] [url] [file]")
}

func parseArgs(args []string) (argument, error) {
	if len(args) > 0 {
		if strings.ToLower(args[0]) == "upload" {
			args = args[1:]

			if len(args) < 3 {
				return argument{}, errors.New("Too few arguments")
			}

			return argument{args[0], args[1], args[2]}, nil
		} else if strings.ToLower(args[0]) == "--help" {
			return argument{}, errors.New("")
		} else {
			return argument{}, fmt.Errorf("Invalid command: %s", args[0])
		}
	}

	return argument{}, errors.New("")
}

func isError(err error) bool {
	return err != nil
}

// Returns url with query params appended
// url - Target URL to append query params to
// qs - Query params to be appended
func addQsToURL(url string, qs map[string]string) string {
	if strings.LastIndex(url, "?") == -1 {
		url = url + "?"
	}

	qsArray := make([]string, len(qs))

	for key, val := range qs {
		qsArray = append(qsArray, fmt.Sprintf("%s=%s", key, val))
	}

	result := url + strings.Join(qsArray, "&")

	return result
}

// Uploads current chunk to the server
// client - Http Client
// url - Target URL for upload
// filename - Filename to be used in multipart file upload
// apikey - Apikey to be passed in Authorization header
// data - Chunk to be uploaded
// returns server response or error, if any
func uploadChunk(client *http.Client, url string, filename string, apikey string, data []byte) (string, error) {
	requestBody := &bytes.Buffer{}
	writer := multipart.NewWriter(requestBody)

	mediaHeader := textproto.MIMEHeader{}
	mediaHeader.Set("Content-Disposition", fmt.Sprintf("form-data; name=\"%s\"; filename=\"%s\"", "file", filename))
	mediaHeader.Set("Content-Type", "application/octet-stream")

	mediaPart, err := writer.CreatePart(mediaHeader)
	mediaPart.Write(data)

	if isError(err) {
		return "", err
	}

	writer.Close()

	req, err := http.NewRequest("POST", url, bytes.NewReader(requestBody.Bytes()))

	if isError(err) {
		return "", err
	}

	req.Header.Set("Authorization", apikey)
	req.Header.Set("Content-Type", fmt.Sprintf("multipart/form-data;boundary=%s", writer.Boundary()))

	resp, err := client.Do(req)

	if isError(err) {
		return "", err
	}

	defer resp.Body.Close()

	statusCode := resp.StatusCode
	body, err := ioutil.ReadAll(resp.Body)

	if isError(err) {
		return "", err
	}

	if statusCode != 200 { // something went wrong, no point proceeding
		return "", fmt.Errorf(string(body))
	}

	return string(body), nil
}

// Uploads a given file in chunks to the server
// args - Arguments passed to the program
func upload(args argument) error {
	apikey := args.APIKey
	url := args.URL
	targetFile := args.File

	file, err := os.Open(targetFile)

	if isError(err) {
		return fmt.Errorf("File doesn't exist: %s", targetFile)
	}

	defer file.Close()

	fileStats, _ := file.Stat()

	resumableTotalSize := fileStats.Size()
	resumableFilename := fileStats.Name()
	resumableIdentifier := fmt.Sprintf("%d-%s", resumableTotalSize, resumableFilename)
	resumableChunks := int((resumableTotalSize / CHUNK_SIZE) + 1)
	resumableChunkSize := CHUNK_SIZE
	client := &http.Client{}
	resumableTotalChunks := resumableChunks
	uploadToken, err := uuid.NewV4()
	
	if err != nil {
		return fmt.Errorf("Error generating uuid: %s", err)
	}
	
	for i := 1; i <= resumableChunks; i++ {
		qs := map[string]string{
			"resumableChunkNumber": fmt.Sprintf("%d", i),
			"uploadToken":					fmt.Sprintf("%s", uploadToken),
		}

		targetURL := addQsToURL(url, qs)

		req, err := http.NewRequest("GET", targetURL, nil)

		if isError(err) {
			return err
		}

		req.Header.Add("Authorization", apikey)

		resp, err := client.Do(req)

		if isError(err) {
			return err
		}

		defer resp.Body.Close()

		statusCode := resp.StatusCode
		body, err := ioutil.ReadAll(resp.Body)

		if isError(err) {
			return err
		}

		// server already has the chunk, proceed to next
		if statusCode == 200 {
			printOK("[%d/%d] Chunk exists!", i, resumableChunks)
		} else if statusCode == 404 { // we are good to read the next chunk and upload to server
			qs = map[string]string{
				"resumableChunkNumber": fmt.Sprintf("%d", i),
				"resumableFilename":    resumableFilename,
				"resumableChunkSize":   fmt.Sprintf("%d", resumableChunkSize),
				"resumableTotalSize":   fmt.Sprintf("%d", resumableTotalSize),
				"resumableIdentifier":  resumableIdentifier,
				"resumableTotalChunks":	fmt.Sprintf("%d", resumableTotalChunks),
				"uploadToken":					fmt.Sprintf("%s", uploadToken),
			}

			targetURL = addQsToURL(url, qs)

			buffer := make([]byte, resumableChunkSize)

			bytesRead, err := file.Read(buffer)

			if isError(err) {
				return err
			}

			// bytes read can be lesser than chunk size, so we take only that
			buffer = buffer[:bytesRead]

			printOK("[%d/%d] Uploading chunk of size %d bytes", i, resumableChunks, bytesRead)

			responseText, err := uploadChunk(client, targetURL, resumableFilename, apikey, buffer)

			if isError(err) {
				return err
			}

			// last chunk's upload response has the "File Reference" we neeed pass to "Lucy" action
			if i == resumableChunks {
				fmt.Println("Result:")
				printOK(responseText)
			}
		} else { // something went wrong, no point proceeding
			return fmt.Errorf(string(body))
		}
	}

	return nil
}

func main() {
	argsWithoutProg := os.Args[1:]

	args, err := parseArgs(argsWithoutProg)

	if err != nil {
		printUsage()

		if err.Error() == "" {
			os.Exit(0)
		}

		printError("Error: %s", err.Error())

		os.Exit(1)
	}

	err = upload(args)

	if err != nil {
		printError("Error: %s", err.Error())

		os.Exit(1)
	}
}
