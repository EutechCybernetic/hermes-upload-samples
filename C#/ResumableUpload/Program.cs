using System;
using System.Linq;
using System.IO;
using System.Collections.Generic;
using System.Net.Http;
using RestSharp;
using System.Threading.Tasks;

namespace ResumableUpload
{
    class Arguments {
        internal string ApiKey { get; set; }
        internal string URL { get; set; }
        internal string File { get; set; }
    }

    class Log
    {
        internal static void PrintOK(string format, params object[] args)
        {
            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine(format, args);
            Console.ResetColor();
        }

        internal static void PrintError(string format, params object[] args)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine(format, args);
            Console.ResetColor();
        }

        internal static void PrintWarning(string format, params object[] args)
        {
            Console.ForegroundColor = ConsoleColor.Yellow;
            Console.WriteLine(format, args);
            Console.ResetColor();
        }
    }

    class Program
    {
        const int MB = 1 * 1024 * 1024;
        const int CHUNK_SIZE = 5 * MB;
        const int BUFFER_SIZE = MB;

        static void PrintUsage()
        {
            Console.WriteLine("ResumableUpload");
            Console.WriteLine("===============");
            Console.WriteLine("Usage:\n\tupload [apikey] [url] [file]");
        }

        static Arguments ParseArgs(string[] args)
        {
            if(args.Length > 0)
            {
                if(args[0].ToLower() == "upload")
                {
                    args = args.Skip(1).ToArray();

                    if(args.Length < 3)
                    {
                        throw new ArgumentException("Too few arguments");
                    }

                    return new Arguments
                    {
                        ApiKey = args[0],
                        URL = args[1],
                        File = args[2]
                    };
                }

                if (args[0].ToLower() == "--help")
                {
                    Program.PrintUsage();

                    Environment.Exit(0);
                }

                throw new ArgumentException("Invalid command: " + args[0]);
            }
            else
            {

                Program.PrintUsage();

                Environment.Exit(0);
            }

            return null;
        }

        static async Task<int> Main(string[] args)
        {
            try
            {
                var arguments = ParseArgs(args);

                await Upload(arguments);
            }
            catch (ArgumentException argpExp)
            {
                PrintUsage();

                Log.PrintError("{0}", argpExp.Message);

                return 1;
            }
            catch (Exception exp)
            {
                Log.PrintError("{0}", exp.Message);

                return 1;
            }

            return 0;
        }

        /// <summary>
        /// Adds given query params to target url
        /// </summary>
        /// <param name="url">Target URL to append query params to</param>
        /// <param name="qs">Query params to be appended</param>
        /// <returns>URL string with query params</returns>
        static string AddQsToUrl(string url, IDictionary<string, object> qs)
        {
            string result = url.LastIndexOf('?') == -1 ? url + "?" : url;
            IList<string> qsArray = new List<string>();

            foreach(var kvp in qs)
            {
                qsArray.Add(string.Format("{0}={1}", kvp.Key, kvp.Value));
            }

            return result + string.Join('&', qsArray);
        }

        /// <summary>
        /// Uploads current chunk to the server
        /// </summary>
        /// <param name="url">Target URL for upload</param>
        /// <param name="filename">Filename to be used in multipart file upload</param>
        /// <param name="apikey">Apikey to be passed in Authorization header</param>
        /// <param name="data">Chunk to be uploaded</param>
        /// <returns>Server response</returns>
        static async Task<string> UploadChunk(string url, string filename, string apikey, byte[] data)
        {
            var restClient = new RestClient(url);
            var restRequest = new RestRequest(Method.POST)
                .AddHeader("Authorization", apikey)
                .AddFileBytes("file", data, filename, "application/octet-stream");

            var response = await restClient.ExecuteAsync(restRequest);

            // something went wrong, no point proceeding
            if (response.StatusCode != System.Net.HttpStatusCode.OK)
            {
                throw new Exception(response.Content);
            }

            return response.Content;
        }

        /// <summary>
        /// Uploads a given file in chunks to the server
        /// </summary>
        /// <param name="args">Arguments passed to the program</param>
        /// <returns></returns>
        static async Task Upload(Arguments args)
        {
            string apikey = args.ApiKey;
            string url = args.URL;
            string targetFile = args.File;

            if(!File.Exists(targetFile))
            {
                throw new FileNotFoundException(targetFile, "File doesn't exist");
            }

            using(var fs = new BufferedStream(File.OpenRead(targetFile), BUFFER_SIZE))
            {
                long resumableTotalSize = fs.Length;
                string resumableFilename = Path.GetFileName(targetFile);
                string resumableIdentifier = string.Format("{0}-{1}", resumableTotalSize, resumableFilename);
                int resumableChunks = (int)(resumableTotalSize / CHUNK_SIZE) + 1;
                int resumableChunkSize = CHUNK_SIZE;
                int resumableTotalChunks = resumableChunks;
                string uploadToken = Guid.NewGuid().ToString();

                for(int i = 1; i <= resumableChunks; i++)
                {
                    string targetUrl = AddQsToUrl(url, new Dictionary<string, object>
                    {
                        { "resumableChunkNumber", i },
                        { "resumableFilename", resumableFilename },
                        { "uploadToken", uploadToken }
                    });

                    var restClient = new RestClient(targetUrl);
                    var restRequest = new RestRequest(Method.GET)
                        .AddHeader("Authorization", apikey);

                    var response = await restClient.ExecuteAsync(restRequest);

                    // server already has the chunk, proceed to next
                    if (response.StatusCode == System.Net.HttpStatusCode.OK)
                    {
                        Log.PrintOK("[{0}/{1}] Chunk exists!", i, resumableChunks);
                    }
                    // we are good to read the next chunk and upload to server
                    else if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
                    {
                        targetUrl = AddQsToUrl(url, new Dictionary<string, object>
                        {
                            { "resumableChunkNumber", i },
                            { "resumableFilename", resumableFilename },
                            { "resumableChunkSize", resumableChunkSize },
                            { "resumableTotalSize", resumableTotalSize },
                            { "resumableIdentifier", resumableIdentifier },
                            { "resumableTotalChunks", resumableTotalChunks },
                            { "uploadToken", uploadToken }
                        });
                        byte[] buffer = new byte[CHUNK_SIZE];

                        // it's possible we are uploading only this chunk
                        // so file might not have been read
                        // seek to this chunk's portion in the file stream and read
                        int bytesRead = await fs.ReadAsync(buffer, (i - 1) * CHUNK_SIZE, CHUNK_SIZE);

                        Log.PrintOK("[{0}/{1}] Uploading chunk of size {2} bytes", i, resumableChunks, bytesRead);

                        // bytes read can be lesser than chunk size, so we take only that
                        string responseText = await UploadChunk(targetUrl, resumableFilename, apikey, buffer.SkipLast(CHUNK_SIZE - bytesRead).ToArray());

                        // last chunk's upload response has the "File Reference" we neeed pass to "Lucy" action 
                        if(i == resumableChunks)
                        {
                            Console.WriteLine("Result:");
                            Log.PrintOK("{0}", responseText);
                        }
                    }
                    // something went wrong, no point proceeding
                    else
                    {
                        throw new Exception(response.Content);
                    }
                }
            }
        }
    }
}
