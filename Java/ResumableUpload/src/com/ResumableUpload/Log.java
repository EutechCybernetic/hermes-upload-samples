package com.ResumableUpload;

class Log {
	public static void printOK(String format, Object... args) {
		System.out.println(String.format(format, args));
	}
	
	public static void printError(String format, Object... args) {
		System.out.println(String.format(format, args));
	}
	
	public static void printWarning(String format, Object... args) {
		System.out.println(String.format(format, args));
	}
}
