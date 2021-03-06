/*
 * TDMX - Trusted Domain Messaging eXchange
 * 
 * Enterprise B2B messaging between separate corporations via interoperable cloud service providers.
 * 
 * Copyright (C) 2014 Peter Klauser (http://tdmx.org)
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses/.
 */
package org.tdmx.core.system.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class StringUtils {

	static String LINEFEED = "\n";

	static {
		try {
			String lf = (String) System.getProperty("line.separator");
			LINEFEED = (lf == null) ? "\n" : lf;
		} catch (Throwable t) {
			// Doh.... whatever; most likely SecurityException
		}
	}

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------
	private StringUtils() {
	}

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	public static String getLF() {
		return LINEFEED;
	}

	public static boolean hasText(String text) {
		return text != null && text.length() > 0;
	}

	public static String nullIfEmpty(String str) {
		if (hasText(str)) {
			return str;
		}
		return null;
	}

	public static boolean containsIgnoreCase(String whole, String part) {
		if (hasText(whole) && hasText(part)) {
			return whole.toUpperCase().contains(part.toUpperCase());
		}
		return false;
	}

	public static String truncateToMaxLen(String input, int len) {
		if (input != null && input.length() > 0) {
			if (input.length() > len) {
				return input.substring(0, len);
			}
		}
		return input;
	}

	public static String asString(byte[] bytes, String encoding) {
		String s;
		try {
			s = new String(bytes, encoding);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
		return s;
	}

	public static byte[] asBytes(String string, String encoding) {
		byte[] result;
		try {
			result = string.getBytes(encoding);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
		return result;
	}

	public static String inputStreamAsString(InputStream stream, Charset cs) throws IOException {
		StringWriter sw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sw)) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, cs))) {
				String line = null;
				while ((line = reader.readLine()) != null) {
					pw.println(line);
				}
			}
		}
		return sw.toString();
	}

	/**
	 * 
	 * @param input
	 * @return true if the input is all in lower case.
	 */
	public static boolean isLowerCase(String input) {
		if (input == null) {
			return false;
		}
		String inputLowerCase = input.toLowerCase();
		return inputLowerCase.equals(input) ? true : false;
	}

	/**
	 * 
	 * @param input
	 * @param suffix
	 * @return true if the input endsWith the suffix
	 */
	public static boolean isSuffix(String input, String suffix) {
		if (input == null || suffix == null) {
			return false;
		}

		return input.endsWith(suffix);
	}

	/**
	 * Convenience method to return a String array as a delimited (e.g. CSV) String. E.g. useful for {@code toString()}
	 * implementations.
	 * 
	 * @param arr
	 *            the array to display
	 * @param delim
	 *            the delimiter to use (probably a ",")
	 * @return the delimited String
	 */
	public static String arrayToDelimitedString(Object[] arr, String delim) {
		if (arr == null || arr.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) {
				sb.append(delim);
			}
			sb.append(arr[i]);
		}
		return sb.toString();
	}

	/**
	 * Convenience method to return a String array as a CSV String. E.g. useful for {@code toString()} implementations.
	 * 
	 * @param arr
	 *            the array to display
	 * @return the delimited String
	 */
	public static String arrayToCommaDelimitedString(Object[] arr) {
		return arrayToDelimitedString(arr, ",");
	}

	/**
	 * Convert a list of strings to a CSV format.
	 * 
	 * @param list
	 * @return
	 */
	public static String convertStringListToCsv(List<String> list) {
		if (list == null || list.isEmpty()) {
			return "";
		}
		return arrayToCommaDelimitedString(list.toArray());
	}

	/**
	 * Converts a CSV string to a list of strings.
	 * 
	 * NOTE the comma character may only be used as the separator, there is no comma escaping used.
	 * 
	 * @param csv
	 * @return
	 */
	public static List<String> convertCsvToStringList(String csv) {
		if (!StringUtils.hasText(csv)) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(csv, ",");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (StringUtils.hasText(token)) {
				result.add(token);
			}
		}
		return result;
	}

	/**
	 * Converts a CSV string to an array of strings.
	 * 
	 * NOTE the comma character may only be used as the separator, there is no comma escaping used.
	 * 
	 * @param csv
	 * @return
	 */
	public static String[] convertCsvToStringArray(String csv) {
		List<String> list = convertCsvToStringList(csv);
		return list.toArray(new String[0]);
	}

	/**
	 * Return a property map representation of a CSV.
	 * 
	 * @param csv
	 * @return
	 */
	public static Map<String, String> getPropertyMap(String csv) {
		List<String> params = StringUtils.convertCsvToStringList(csv);
		Map<String, String> paramMap = new HashMap<>();
		for (String p : params) {
			int splitIdx = p.indexOf("=");
			if (splitIdx > 0) {
				paramMap.put(p.substring(0, splitIdx), p.substring(splitIdx + 1));
			}
		}
		return paramMap;
	}

	/**
	 * Convert a PropertyMap into a CSV
	 * 
	 * @param paramMap
	 * @return
	 */
	public static String convertPropertyMapToCSV(Map<String, String> paramMap) {
		List<String> params = new ArrayList<>(paramMap.size());
		for (Map.Entry<String, String> entry : paramMap.entrySet()) {
			params.add(entry.getKey() + "=" + entry.getValue());
		}
		return convertStringListToCsv(params);
	}

	public static String getExceptionSummary(Exception e) {
		StringBuilder sb = new StringBuilder();
		if (e != null) {
			String clazz = e.getClass().getName();
			String msg = e.getMessage();
			if (!StringUtils.hasText(msg) || !msg.contains(clazz)) {
				sb.append(clazz).append(": ").append(msg);
			} else {
				sb.append(msg);
			}
			sb.append(msg);
			if (e.getCause() != null) {
				sb.append(" Cause: ").append(e.getCause().getMessage());
			}
		}
		return sb.toString();
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------
}
