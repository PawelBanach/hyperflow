const FUNCTION_TYPE = process.env.FUNCTION_TYPE ? process.env.FUNCTION_TYPE : "2048"; //default resource

const GOOGLE_BUCKET = "BUCKET_NAME";
const GOOGLE_PATH   = "BUCKET_PATH"; //e.g.data/0.25

exports.functionType = FUNCTION_TYPE;

exports.resources = {
  "128": "function_url_128",
  "256": "function_url_256",
  "512": "function_url_512",
  "1024": "function_url_1024",
  "2048": "function_url_2048"
};

// Google cloud storage
exports.options = {
  "storage": "google",
  "bucket": GOOGLE_BUCKET,
  "prefix": GOOGLE_PATH
};
