const FUNCTION_TYPE = process.env.FUNCTION_TYPE ? process.env.FUNCTION_TYPE : "1536"; // default resource

const AWS_BUCKET = "BUCKET_NAME";
const AWS_PATH = "BUCKET_PATH"; //e.g.data/0.25

exports.functionType = FUNCTION_TYPE;

exports.resources = {
  "128": "function_url_128",
  "256": "function_url_256",
  "512": "function_url_512",
  "1024": "function_url_1024",
  "1536": "function_url_1536"
};

exports.options = {
  "storage": "aws",
  "bucket": AWS_BUCKET,
  "prefix": AWS_PATH
};
