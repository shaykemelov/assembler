package edu.shaykemelov.assembler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public class Assembler
{
	/**
	 * Паттерн для поиска комментариев
	 */
	private static final Pattern COMMENTS_PATTERN = Pattern.compile("//.*");

	/**
	 * Паттерн для поиска меток
	 */
	private static final Pattern LABELS_PATTERN = Pattern.compile("\\(.*\\)");

	/**
	 * Предопределенные переменные
	 */
	private static final Map<String, Integer> PREDEFINED_VARIABLES = Map.ofEntries(
			Map.entry("R0", 0),
			Map.entry("R1", 1),
			Map.entry("R2", 2),
			Map.entry("R3", 3),
			Map.entry("R4", 4),
			Map.entry("R5", 5),
			Map.entry("R6", 6),
			Map.entry("R7", 7),
			Map.entry("R8", 8),
			Map.entry("R9", 9),
			Map.entry("R1O", 10),
			Map.entry("R11", 11),
			Map.entry("R12", 12),
			Map.entry("R13", 13),
			Map.entry("R14", 14),
			Map.entry("R15", 15),
			Map.entry("SCREEN", 16384),
			Map.entry("KBD", 24576),
			Map.entry("SP", 0),
			Map.entry("LCL", 1),
			Map.entry("ARG", 2),
			Map.entry("THIS", 3),
			Map.entry("THAT", 4)
	);

	private static final Map<String, String> COMPUTATIONS = Map.ofEntries(
			Map.entry("null", "0000000"),
			Map.entry("0", "0101010"),
			Map.entry("1", "0111111"),
			Map.entry("-1", "0111010"),
			Map.entry("D", "0001100"),
			Map.entry("A", "0110000"),
			Map.entry("M", "1110000"),
			Map.entry("!D", "0001101"),
			Map.entry("!A", "0110001"),
			Map.entry("!M", "1110001"),
			Map.entry("-D", "0001111"),
			Map.entry("-A", "0110111"),
			Map.entry("D+1", "0011111"),
			Map.entry("A+1", "0110111"),
			Map.entry("M+1", "1110111"),
			Map.entry("D-1", "0001110"),
			Map.entry("A-1", "0110010"),
			Map.entry("M-1", "1110010"),
			Map.entry("D+A", "0000010"),
			Map.entry("D+M", "1000010"),
			Map.entry("D-A", "0010011"),
			Map.entry("D-M", "1010011"),
			Map.entry("A-D", "0000111"),
			Map.entry("M-D", "1000111"),
			Map.entry("D&A", "0000000"),
			Map.entry("D&M", "1000000"),
			Map.entry("D|A", "0010101"),
			Map.entry("D|M", "1010101")
	);

	private static final Map<String, String> DESTINATIONS = Map.of(
			"null", "000",
			"M", "001",
			"D", "010",
			"MD", "011",
			"A", "100",
			"AM", "101",
			"AD", "110",
			"ADM", "111"
	);

	private static final Map<String, String> JUMPS = Map.of(
			"null", "000",
			"JGT", "001",
			"JEQ", "010",
			"JGE", "011",
			"JLT", "100",
			"JNE", "101",
			"JLE", "110",
			"JMP", "111"
	);

	private int variablesCounter = 16;

	public static void main(final String[] args) throws IOException
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("Укажите путь до файла с исходным кодом и путь до места назначения");
		}

		final var sourceFilepathAsString = args[0];

		final var sourceFilepath = Path.of(sourceFilepathAsString);

		if (Files.notExists(sourceFilepath))
		{
			throw new IllegalStateException("Файл с указанным путем не существует");
		}

		final var assembler = new Assembler();
		final var assembledInstructions = assembler.assemble(sourceFilepath);

		final var destinationFilepath = args[1];
		final var destinationPath = Path.of(destinationFilepath);

		assembledInstructions.forEach(instruction ->
		{
			try
			{
				Files.writeString(destinationPath, instruction + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		});
	}

	private List<String> assemble(final Path sourceFilepath) throws IOException
	{
		final var sourceInstructions = Files.readAllLines(sourceFilepath)
				.stream()
				.map(Assembler::removeComments)
				.map(String::strip)
				.filter(not(String::isBlank))
				.collect(Collectors.toList());

		final var labels = extractLabels(sourceInstructions);
		final var variables = new HashMap<String, Integer>();
		final var instructions = new ArrayList<String>();

		for (final var instruction : sourceInstructions)
		{
			if (instruction.startsWith("@"))
			{
				final var aInstruction = assembleAInstruction(instruction, variables, labels);
				instructions.add(aInstruction);
				continue;
			}

			final var indexOfSign = instruction.indexOf("=");
			var computation = indexOfSign != -1 ? instruction.substring(indexOfSign + 1) : null;

			final int indexOfSemiColumn = instruction.indexOf(";");
			final var jump = indexOfSemiColumn != -1 ? instruction.substring(indexOfSemiColumn + 1) : null;

			final String destination;
			if (computation != null)
			{
				destination = instruction.substring(0, indexOfSign);
			}
			else if (jump != null)
			{
				computation = instruction.substring(0, indexOfSemiColumn);
				destination = null;
			}
			else
			{
				destination = instruction;
			}

			final var cInstruction = assembleCInstruction(
					String.valueOf(computation),
					String.valueOf(destination),
					String.valueOf(jump));

			instructions.add(cInstruction);
		}

		return instructions;
	}

	private String assembleAInstruction(final String rawAInstruction,
										final Map<String, Integer> variables,
										final Map<String, Integer> labels)
	{
		final String rawValue = rawAInstruction.substring(1);

		int value;

		try
		{
			value = Integer.parseUnsignedInt(rawValue);
		}
		catch (final NumberFormatException e)
		{
			final Integer predefinedVariable = PREDEFINED_VARIABLES.get(rawValue);
			final Integer variable = variables.get(rawValue);
			final Integer label = labels.get(rawValue);

			if (predefinedVariable != null)
			{
				value = predefinedVariable;
			}
			else if (variable != null)
			{
				value = variable;
			}
			else if (label != null)
			{
				value = label;
			}
			else
			{
				value = variablesCounter++;
				variables.put(rawValue, value);
			}
		}

		final var binaryValueWithoutLeadingZeros = Integer.toBinaryString(value);
		final var zeros = 16 - binaryValueWithoutLeadingZeros.length();

		return "0".repeat(Math.max(0, zeros)) + binaryValueWithoutLeadingZeros;
	}

	private String assembleCInstruction(final String computation,
										final String destination,
										final String jump)
	{
		return "111" + COMPUTATIONS.get(computation) + DESTINATIONS.get(destination) + JUMPS.get(jump);
	}

	/**
	 * Найти метки в инструкциях и вернуть их
	 *
	 * @param instructions инструкции в которых нужно найти метки
	 * @return пары меток и адресов инструкций
	 */
	private Map<String, Integer> extractLabels(final List<String> instructions)
	{
		var labels = new HashMap<String, Integer>();
		var instructionsCount = 0;

		final ListIterator<String> iterator = instructions.listIterator();
		while (iterator.hasNext())
		{
			final String instruction = iterator.next();

			final var matcher = LABELS_PATTERN.matcher(instruction);

			if (matcher.find())
			{
				final var label = matcher.group();
				labels.put(label.substring(1, label.length() - 1), instructionsCount);
				iterator.remove();
			}
			else
			{
				// увеличивает счетчик инструкций только если это не метка
				// т.к. метка не является инструкцией
				instructionsCount++;
			}
		}

		return labels;
	}

	/**
	 * Удалить комментарии. Будет удалено всё, что начинается с // и до конца строки
	 *
	 * @param instruction инструкция из которой нужно удалить комментарий
	 * @return инструкция без комментария
	 */
	private static String removeComments(final String instruction)
	{
		final var matcher = COMMENTS_PATTERN.matcher(instruction);

		if (matcher.find())
		{
			return matcher.replaceAll("");
		}

		return instruction;
	}
}