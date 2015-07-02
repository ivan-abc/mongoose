package com.emc.mongoose.common.conf;
//
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
//
/**
 * Created by gusakk on 3/13/15.
 */
public class JsonConfigLoader {
	private final static Logger LOG = LogManager.getLogger();
	private final static Set<String> mongooseKeys = new HashSet<>();
	//
	public static enum JsonConfigLoaderActions {
		LOAD, UPDATE, UPLOAD
	}
	//
	private static RunTimeConfig DEFAULT_CFG;
	private static JsonConfigLoaderActions ACTION = JsonConfigLoaderActions.LOAD;
	//
	private final RunTimeConfig tgtConfig;
	//
	public JsonConfigLoader(final RunTimeConfig tgtConfig) {
		this.tgtConfig = tgtConfig;
	}
	//
	public static void loadPropsFromJsonCfgFile(final Path propsDir, final RunTimeConfig tgtConfig) {
		DEFAULT_CFG = tgtConfig;
		//
		final File cfgFile = propsDir.toFile();
		final ObjectMapper jsonMapper = new ObjectMapper();
		//
		try {
			JsonNode rootNode;
			if(cfgFile.exists() && cfgFile.isFile()){
				LOG.debug(Markers.MSG, "Load the config from json file \"{}\"", cfgFile.toString());
				rootNode = jsonMapper.readTree(cfgFile);
			} else {
				final ClassLoader cl = JsonConfigLoader.class.getClassLoader();
				final InputStream bundledConf = cl.getResourceAsStream(RunTimeConfig.FNAME_CONF);
				LOG.debug(
					Markers.MSG, "Load the bundled config", cl.getResource(RunTimeConfig.FNAME_CONF)
				);
				rootNode = jsonMapper.readTree(bundledConf);
			}
			walkJsonTree(rootNode);
			tgtConfig.setMongooseKeys(mongooseKeys);
			tgtConfig.putJsonProps(rootNode);
			//
			if (ACTION.equals(JsonConfigLoaderActions.UPLOAD)) {
				jsonMapper.writerWithDefaultPrettyPrinter().writeValue(cfgFile, rootNode);
			}
		} catch(final IOException e) {
			LogUtil.exception(
				LOG, Level.ERROR, e, "Failed to load properties from \"{}\"", cfgFile
			);
		}
	}
	//
	private static void walkJsonTree(final JsonNode jsonNode) {
		walkJsonTree(jsonNode, null);
	}
	//
	private static void walkJsonTree(final JsonNode jsonNode, final String fullFileName) {
		final Iterator<String> fields = jsonNode.fieldNames();
		while (fields.hasNext()) {
			final String shortFieldName = fields.next();
			String fullFieldName = "";
			if (fullFileName != null) {
				if (fullFileName.isEmpty()) {
					fullFieldName = shortFieldName;
				} else {
					fullFieldName = fullFileName + Constants.DOT + shortFieldName;
				}
			}
			if (!jsonNode.get(shortFieldName).fieldNames().hasNext()) {
				if (ACTION.equals(JsonConfigLoaderActions.UPDATE)
						|| ACTION.equals(JsonConfigLoaderActions.UPLOAD)) {
					final String property = DEFAULT_CFG.getProperty(fullFieldName).toString()
							.replace("[", "")
							.replace("]", "")
							.replace(" ", "")
							.trim();
					DEFAULT_CFG.setProperty(fullFieldName, DEFAULT_CFG.getProperty(fullFieldName));
					((ObjectNode) jsonNode).put(shortFieldName, property);
				} else {
					DEFAULT_CFG.setProperty(fullFieldName, jsonNode.get(shortFieldName).toString()
                            .replace("[", "")
                            .replace("]", "")
                            .replace(" ", "")
							.replace("\"", "")
							.trim());
					mongooseKeys.add(fullFieldName);
				}
			} else {
				walkJsonTree(jsonNode.get(shortFieldName), fullFieldName);
			}
		}
	}
	//
	public static void updateProps(
		final Path rootDir, final RunTimeConfig tgtConfig, final boolean isUpload
	) {
		if (isUpload) {
			ACTION = JsonConfigLoaderActions.UPLOAD;
		} else {
			ACTION = JsonConfigLoaderActions.UPDATE;
		}
		loadPropsFromJsonCfgFile(rootDir, tgtConfig);
	}
}
