/**
 * 
 */
package com.twitter.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a variety of text file handling methods, including CSV reading and writing
 * 
 * @author owaishussain@outlook.com
 */
public class FileUtil {

	/**
	 * Read a file line by line and return the contents as a list of strings
	 * 
	 * @param filePath
	 * @return
	 * @throws IOException
	 */
	public List<String> readLines(String filePath) throws IOException {
		ArrayList<String> content = new ArrayList<String>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(filePath));
			String line = "";
			while ((line = in.readLine()) != null) {
				content.add(line);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			in.close();
		}
		return content;
	}
	
	/**
	 * Writes the content to the file on given path using separator and qualifier characters
	 * 
	 * @param filePath
	 * @param content
	 * @param seprator
	 * @param useQualifiers
	 * @param append
	 * @throws IOException
	 */
	public void writeCsv(String filePath, List<String[]> content, char seprator, boolean useQualifiers, boolean append)
	        throws IOException {
		String qualifier = useQualifiers ? "\"" : "";
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(filePath, append));
			for (String[] row : content) {
				StringBuilder line = new StringBuilder();
				for (String value : row) {
					line.append(qualifier).append(value).append(qualifier).append(seprator);
				}
				out.write(line.subSequence(0, line.length() - 1) + "\n");
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			out.close();
		}
	}

}
