package edu.shaykemelov.assembler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		Map.entry("RO", 0),
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

	/**
	 * Метки. k — имя метки, v — адрес
	 */
	private final Map<String, Integer> labels = new HashMap<>();

	/**
	 * Переменные. k — имя переменной, v — адрес
	 */
	private final Map<String, Integer> variables = new HashMap<>();

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
		final var instructions = Files.readAllLines(sourceFilepath)
		                              .stream()
		                              .map(String::strip)
		                              .map(Assembler::removeComments)
		                              .filter(not(String::isBlank))
		                              .collect(Collectors.toList());

		labels.putAll(extractLabels(instructions));

		return instructions;
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

		for (final var instruction : instructions)
		{
			final var matcher = LABELS_PATTERN.matcher(instruction);

			if (matcher.find())
			{
				final var label = matcher.group();
				labels.put(label, instructionsCount);
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