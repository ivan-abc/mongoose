package com.emc.mongoose.scenario.step;

import com.emc.mongoose.api.model.concurrent.LogContextThreadFactory;
import com.emc.mongoose.ui.config.Config;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Loggers;
import static com.emc.mongoose.ui.log.LogUtil.BLUE;
import static com.emc.mongoose.ui.log.LogUtil.CYAN;
import static com.emc.mongoose.ui.log.LogUtil.RED;
import static com.emc.mongoose.ui.log.LogUtil.RESET;

import org.apache.logging.log4j.Level;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 The specific scenario step executing a shell command.
 */
public class CommandStep
extends ConfigurableStepBase
implements ValueStep {

	private static final ThreadFactory TF_STD_IN = new LogContextThreadFactory("stdInReader", true);
	private static final ThreadFactory TF_STD_ERR = new LogContextThreadFactory("stdErrReader", true);

	private final String cmd;

	public CommandStep(final Config baseConfig) {
		this(baseConfig, null, null);
	}

	private CommandStep(
		final Config baseConfig, final List<Map<String, Object>> stepConfigs, final String cmd
	) {
		super(baseConfig, stepConfigs);
		this.cmd = cmd;
	}

	@Override
	protected CommandStep copyInstance(final List<Map<String, Object>> stepConfigs) {
		return new CommandStep(baseConfig, stepConfigs, cmd);
	}

	@Override
	protected String getTypeName() {
		return "command";
	}

	@Override
	public CommandStep value(final String value) {
		return new CommandStep(baseConfig, stepConfigs, value);
	}

	@Override
	protected void invoke(final Config config)
	throws Throwable {

		final boolean stdOutColorFlag = config.getOutputConfig().getColor();
		Loggers.MSG.info(
			"Invoking the shell command:\n{}{}{}",
			stdOutColorFlag ? CYAN : "", cmd, stdOutColorFlag ? RESET : ""
		);
		final Process process = new ProcessBuilder("sh", "-c", cmd).start();
		final Thread processStdInReader = TF_STD_IN.newThread(
			() -> {
				try(
					final BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(process.getInputStream())
					)
				) {
					String nextLine;
					while(null != (nextLine = bufferedReader.readLine())) {
						Loggers.MSG.info(
							"{}{}{}", stdOutColorFlag ? BLUE : "", nextLine,
							stdOutColorFlag ? RESET : ""
						);
					}
				} catch(final IOException e) {
					LogUtil.exception(
						Level.DEBUG, e, "Failed to read the process stdin"
					);
				}
			}
		);
		final Thread processStdErrReader = TF_STD_ERR.newThread(
			() -> {
				try(
					final BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(process.getErrorStream())
					)
				) {
					String nextLine;
					while(null != (nextLine = bufferedReader.readLine())) {
						Loggers.MSG.info(
							"{}{}{}", stdOutColorFlag ? RED : "", nextLine,
							stdOutColorFlag ? RESET : ""
						);
					}
				} catch(final IOException e) {
					LogUtil.exception(
						Level.DEBUG, e, "Failed to read the process error input"
					);
				}
			}
		);
		processStdInReader.start();
		processStdErrReader.start();
		try {
			final int exitCode = process.waitFor();
			if(exitCode == 0) {
				Loggers.MSG.info("Shell command \"{}\" finished", cmd);
			} else {
				Loggers.ERR.warn(
					"Shell command \"{}\" finished with exit code {}", cmd, exitCode
				);
			}
		} finally {
			processStdInReader.interrupt();
			processStdErrReader.interrupt();
			process.destroy();
		}
	}
}
