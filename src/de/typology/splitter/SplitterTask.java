package de.typology.splitter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.typology.indexes.WordIndex;

/**
 * A class for running Sequencer and Aggregator for a given pattern.
 * 
 * @author Martin Koerner
 * 
 */
public class SplitterTask implements Runnable {
	protected InputStream inputStream;
	protected File outputDirectory;
	protected WordIndex wordIndex;
	protected boolean[] pattern;
	protected String patternLabel;
	protected String delimiter;
	protected boolean deleteTempFiles;
	int startSortAtColumn;

	static Logger logger = LogManager.getLogger(SmoothingSplitter.class
			.getName());

	public SplitterTask(InputStream inputStream, File outputDirectory,
			WordIndex wordIndex, boolean[] pattern, String patternLabel,
			String delimiter, int startSortAtColumn, boolean deleteTempFiles) {
		this.inputStream = inputStream;
		this.outputDirectory = outputDirectory;
		this.wordIndex = wordIndex;
		this.pattern = pattern;
		this.patternLabel = patternLabel;
		this.delimiter = delimiter;
		this.deleteTempFiles = deleteTempFiles;
	}

	@Override
	public void run() {
		File sequencerOutputDirectory = new File(
				this.outputDirectory.getAbsolutePath() + "/"
						+ this.patternLabel + "-split");
		if (sequencerOutputDirectory.exists()) {
			try {
				FileUtils.deleteDirectory(sequencerOutputDirectory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		sequencerOutputDirectory.mkdir();
		logger.info("start building: "
				+ sequencerOutputDirectory.getAbsolutePath());

		// initialize sequencer
		Sequencer sequencer = new Sequencer(this.inputStream,
				sequencerOutputDirectory, this.wordIndex, this.pattern);
		// TODO change method name
		sequencer.run();

		File aggregatedOutputDirectory = new File(
				this.outputDirectory.getAbsolutePath() + "/"
						+ this.patternLabel);
		if (aggregatedOutputDirectory.exists()) {
			try {
				FileUtils.deleteDirectory(aggregatedOutputDirectory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		aggregatedOutputDirectory.mkdir();
		logger.info("aggregate into: " + aggregatedOutputDirectory);

		for (File splitFile : sequencerOutputDirectory.listFiles()) {
			Aggregator aggregator = new Aggregator(splitFile, new File(
					aggregatedOutputDirectory.getAbsolutePath() + "/"
							+ splitFile.getName()), this.delimiter, 0);
			// TODO change method name
			aggregator.run();
		}

		// delete sequencerOutputDirectory
		if (this.deleteTempFiles) {
			try {
				FileUtils.deleteDirectory(sequencerOutputDirectory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
