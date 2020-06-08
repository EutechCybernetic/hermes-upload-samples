#[derive(Debug)]
pub struct UploadError {
  message: String
}

impl UploadError {
  pub fn new(message: String) -> Self {
    Self { message }
  }
}

impl std::fmt::Display for UploadError {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
      write!(f, "{}", self.message)
  }
}

impl std::error::Error for UploadError {}