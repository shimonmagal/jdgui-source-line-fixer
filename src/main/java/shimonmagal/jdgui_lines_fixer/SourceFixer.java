package shimonmagal.jdgui_lines_fixer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SourceFixer
{
	private static final Pattern pattern = Pattern.compile("/\\*[\\s]*([0-9]+)[\\s]*\\*/");
	private static final String newLine = System.getProperty("line.seperator", "\n");

	public static void main(String[] args)
	{
		if (args.length != 2)
		{
			System.out.println("Usage: javap -jar jdgui-lines-fixer.jar /path/to/zipfile /path/to/output");
			System.exit(1);
		}

		try
		{
			handleZip(args[0], args[1]);
		}
		catch (IOException e)
		{
			System.err.println("Bad input file given");
			e.printStackTrace();
		}
	}

	private static void handleZip(String srcFile, String outputFile) throws IOException
	{
		File srcZipFile = new File(srcFile);

		File outputZipFile = new File(outputFile);

		try (
				ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZipFile));
				ZipInputStream zis = new ZipInputStream(new FileInputStream(srcZipFile));)
		{
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null)
			{
				String entryName = entry.getName();

				handleSingleEntry(zis, entryName, zos);
			}
		}
	}

	private static void handleSingleEntry(ZipInputStream zis, String entryName, ZipOutputStream zos)
			throws IOException
	{
		Map<Integer, List<String>> mapping = new HashMap<>();

		int maxLineNumber = scanFileAndIndexLineNumbers(zis, mapping);

		ZipEntry entry = new ZipEntry(entryName);
		zos.putNextEntry(entry);

		rewritefile(zos, mapping, maxLineNumber);

		zos.closeEntry();
	}

	private static int scanFileAndIndexLineNumbers(ZipInputStream zis, Map<Integer, List<String>> mapping)
			throws NumberFormatException, IOException
	{
		Integer currLineNumber = 1;
		Integer maxLine = 1;

		String currentLine;

		InputStreamReader isr = new InputStreamReader(zis);
		BufferedReader br = new BufferedReader(isr);
			while ((currentLine = br.readLine()) != null)
			{
				Matcher matcher = pattern.matcher(currentLine);

				if (matcher.find())
				{
					String lineNumber = matcher.group(1);
					currLineNumber = Integer.parseInt(lineNumber);
					maxLine = maxLine < currLineNumber ? currLineNumber : maxLine;
					putInMultiMap(mapping, currLineNumber, currentLine);
				}
				else
				{
					putInMultiMap(mapping, currLineNumber, currentLine);
				}
			}

		return maxLine;
	}

	private static void rewritefile(ZipOutputStream zos, Map<Integer, List<String>> mapping, int maxLine)
			throws IOException
	{
		Integer lineNumberInComment = findNextLineNumberInComment(mapping, 1, maxLine);

		if (lineNumberInComment == null)
		{
			return;
		}

		for (int lineNumber = lineNumberInComment; lineNumber <= maxLine;)
		{
			if (lineNumberInComment == null)
			{
				break;
			}

			Integer nextNotNullLocation = findNextLineNumberInComment(mapping, lineNumber + 1, maxLine);

			List<String> lines = mapping.get(lineNumber);
			for (String line : lines)
			{
				writeStrToOs(line, zos);

				if (nextNotNullLocation == null || lineNumber < nextNotNullLocation)
				{
					writeStrToOs(newLine, zos);
					lineNumber++;
				}
			}

			while ((nextNotNullLocation == null || lineNumber < nextNotNullLocation) && lineNumber < maxLine)
			{
				writeStrToOs(newLine, zos);
				lineNumber++;
			}

			lineNumberInComment = nextNotNullLocation;
		}
	}

	private static Integer findNextLineNumberInComment(Map<Integer, List<String>> map, Integer i, Integer max)
	{
		for (Integer j = i; j <= max; j++)
		{
			if (map.get(j) != null)
			{
				return j;
			}
		}

		return null;
	}

	private static void putInMultiMap(Map<Integer, List<String>> mapping, int currLineNumber, String line)
	{
		List<String> lines = mapping.get(currLineNumber);

		if (lines == null)
		{
			lines = new LinkedList<>();

			mapping.put(currLineNumber, lines);
		}

		lines.add(line);
	}

	private static void writeStrToOs(String str, OutputStream os) throws IOException
	{
		for (int ch : str.value)
		{
			os.write(ch);
		}
	}
}
