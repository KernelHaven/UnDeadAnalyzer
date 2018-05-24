package net.ssehub.kernel_haven.undead_analyzer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.Setting;

/**
 * This class represents the settings for incremental analyses.
 * 
 * @author moritz
 */
public class UndeadSettings {

	public static final Setting<Integer> NUMBER_OF_OF_THREADS = new Setting<Integer>("undead.threaded.threads",
			Setting.Type.INTEGER, true, "2",
			"Number of threads to use for the " + ThreadedDeadCodeFinder.class.getSimpleName());
	
	/**
	 * Holds all declared setting constants.
	 */
	private static final Set<Setting<?>> SETTINGS = new HashSet<>();

	static {
		for (Field field : UndeadSettings.class.getFields()) {
			if (Setting.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())
					&& Modifier.isFinal(field.getModifiers())) {
				try {
					SETTINGS.add((Setting<?>) field.get(null));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Registers all settings declared in this class to the given configuration
	 * object.
	 * 
	 * @param config
	 *            The configuration to register the settings to.
	 * 
	 * @throws SetUpException
	 *             If any setting restrictions are violated.
	 */
	public static void registerAllSettings(Configuration config) throws SetUpException {
		for (Setting<?> setting : SETTINGS) {
			config.registerSetting(setting);
		}
	}

}
