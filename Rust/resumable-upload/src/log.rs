use colored::*;

pub struct Log;

impl Log {
    pub fn print_ok(text: String) {
        println!("{}", text.green())
    }

    pub fn print_error(text: String) {
        println!("{}", text.red())
    }
}