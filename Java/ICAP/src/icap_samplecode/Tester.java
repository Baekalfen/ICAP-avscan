package icap_samplecode;

import java.io.IOException;

public class Tester {
	public static void main(String[] args) {
		try {
			ICAP icap = new ICAP("192.168.1.5", 1344, "avscan");

			String[] files = new String[]{
				"C:\\Users\\Mads\\Downloads\\eicar.com.txt",
				"C:\\Users\\Mads\\Downloads\\eicar.com2.txt",
				"C:\\Users\\Mads\\Downloads\\eicar.com.txt",
				"C:\\Users\\Mads\\Downloads\\eicar.com2.txt",
				"C:\\Users\\Mads\\Downloads\\eicar.com.txt",
				"C:\\Users\\Mads\\Downloads\\eicar.com2.txt",
				"C:\\Users\\Mads\\Downloads\\Git-1.8.4-preview20130916.exe"
			};

			for (String file : files) {
				try {
					System.out.print(file + ": ");
					boolean result = icap.scanFile(file);
					System.out.println(result ? "Clean" : "Infected");
				} catch (ICAPException ex) {
					System.err.println("Could not scan file " + file + ": " + ex.getMessage());
				} catch (IOException ex) {
					System.err.println("IO error occurred when scanning file " + file + ": " + ex.getMessage());
				}
			}
		} catch (IOException | ICAPException e) {
			System.out.println(e.getMessage());
		}

	}
}
