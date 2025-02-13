/*
   Copyright 2013, 2016 Nationale-Nederlanden, 2020-2021 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import nl.nn.adapterframework.cache.IbisCacheManager;
import nl.nn.adapterframework.configuration.classloaders.IConfigurationClassLoader;
import nl.nn.adapterframework.core.Adapter;
import nl.nn.adapterframework.core.IAdapter;
import nl.nn.adapterframework.core.INamedObject;
import nl.nn.adapterframework.core.IScopeProvider;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.scheduler.JobDef;
import nl.nn.adapterframework.statistics.HasStatistics;
import nl.nn.adapterframework.statistics.StatisticsKeeperIterationHandler;
import nl.nn.adapterframework.statistics.StatisticsKeeperLogger;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.RunStateEnum;

/**
 * The Configuration is placeholder of all configuration objects. Besides that, it provides
 * functions for starting and stopping adapters as a facade.
 *
 * @author Johan Verrips
 * @see    nl.nn.adapterframework.configuration.ConfigurationException
 * @see    nl.nn.adapterframework.core.Adapter
 */
public class Configuration extends ClassPathXmlApplicationContext implements INamedObject, ApplicationContextAware, IScopeProvider {
	protected Logger log = LogUtil.getLogger(this);

	private Boolean autoStart = null;

	private IAdapterService adapterService;

	private List<Runnable> startAdapterThreads = Collections.synchronizedList(new ArrayList<Runnable>());
	private List<Runnable> stopAdapterThreads = Collections.synchronizedList(new ArrayList<Runnable>());
	private boolean unloadInProgressOrDone = false;

	private final Map<String, JobDef> jobTable = new LinkedHashMap<>(); // TODO useless synchronization ?
	private final List<JobDef> scheduledJobs = new ArrayList<>();

	private String version;
	private IbisManager ibisManager;
	private String originalConfiguration;
	private String loadedConfiguration;
	private StatisticsKeeperIterationHandler statisticsHandler = null;

	private ConfigurationException configurationException = null;
	private BaseConfigurationWarnings configurationWarnings = new BaseConfigurationWarnings();

	private Date statisticsMarkDateMain=new Date();
	private Date statisticsMarkDateDetails=statisticsMarkDateMain;

	public void forEachStatisticsKeeper(StatisticsKeeperIterationHandler hski, Date now, Date mainMark, Date detailMark, int action) throws SenderException {
		Object root = hski.start(now,mainMark,detailMark);
		try {
			Object groupData=hski.openGroup(root,AppConstants.getInstance().getString("instance.name",""),"instance");
			for (Adapter adapter : adapterService.getAdapters().values()) {
				adapter.forEachStatisticsKeeperBody(hski,groupData,action);
			}
			IbisCacheManager.iterateOverStatistics(hski, groupData, action);
			hski.closeGroup(groupData);
		} finally {
			hski.end(root);
		}
	}

	public void dumpStatistics(int action) {
		Date now = new Date();
		boolean showDetails=(action == HasStatistics.STATISTICS_ACTION_FULL ||
							 action == HasStatistics.STATISTICS_ACTION_MARK_FULL ||
							 action == HasStatistics.STATISTICS_ACTION_RESET);
		try {
			if (statisticsHandler==null) {
				statisticsHandler =new StatisticsKeeperLogger();
				statisticsHandler.configure();
			}

//			StatisticsKeeperIterationHandlerCollection skihc = new StatisticsKeeperIterationHandlerCollection();
//
//			StatisticsKeeperLogger skl =new StatisticsKeeperLogger();
//			skl.configure();
//			skihc.registerIterationHandler(skl);
//
//			StatisticsKeeperStore skih = new StatisticsKeeperStore();
//			skih.setJmsRealm("lokaal");
//			skih.configure();
//			skihc.registerIterationHandler(skih);

			forEachStatisticsKeeper(statisticsHandler, now, statisticsMarkDateMain, showDetails ?statisticsMarkDateDetails : null, action);
		} catch (Exception e) {
			log.error("dumpStatistics() caught exception", e);
		}
		if (action==HasStatistics.STATISTICS_ACTION_RESET ||
			action==HasStatistics.STATISTICS_ACTION_MARK_MAIN ||
			action==HasStatistics.STATISTICS_ACTION_MARK_FULL) {
				statisticsMarkDateMain=now;
		}
		if (action==HasStatistics.STATISTICS_ACTION_RESET ||
			action==HasStatistics.STATISTICS_ACTION_MARK_FULL) {
				statisticsMarkDateDetails=now;
		}

	}

	public Configuration() {
		setConfigLocation("FrankFrameworkConfigurationContext.xml"); //Don't call the super(..), it will trigger a refresh.
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		setParent(applicationContext);
	}

	@Override
	public void afterPropertiesSet() {
		if(!(getClassLoader() instanceof IConfigurationClassLoader)) {
			throw new IllegalStateException("No IConfigurationClassLoader set");
		}
		if(ibisManager == null) {
			throw new IllegalStateException("No IbisManager set");
		}

		setVersion(ConfigurationUtils.getConfigurationVersion(getClassLoader()));
		if(StringUtils.isEmpty(getVersion())) {
			log.info("unable to determine [configuration.version] for configuration [{}]", ()-> getName());
		} else {
			log.debug("configuration [{}] found currentConfigurationVersion [{}]", ()-> getName(), ()-> getVersion());
		}

		super.afterPropertiesSet(); //Triggers a context refresh

		log.info("initialized Configuration [{}] with ClassLoader [{}]", ()-> toString(), ()-> getClassLoader());
	}

	public void setAutoStart(boolean autoStart) {
		this.autoStart = autoStart;
	}

	public boolean isAutoStart() {
		if(autoStart == null && getClassLoader() != null) {
			autoStart = AppConstants.getInstance(getClassLoader()).getBoolean("configurations.autoStart", true);
		}
		return autoStart;
	}

	public boolean isStubbed() {
		if(getClassLoader() == null) {
			return false;
		}

		return ConfigurationUtils.isConfigurationStubbed(getClassLoader());
	}

	/**
	 * Get a registered adapter by its name through {@link IAdapterService#getAdapter(String)}
	 * @param name the adapter to retrieve
	 * @return IAdapter
	 */
	public Adapter getRegisteredAdapter(String name) {
		return adapterService.getAdapter(name);
	}

	public Adapter getRegisteredAdapter(int index) {
		return getRegisteredAdapters().get(index);
	}

	public List<Adapter> getRegisteredAdapters() {
		return new ArrayList<>(adapterService.getAdapters().values());
	}

	public List<String> getSortedStartedAdapterNames() {
		List<String> startedAdapters = new ArrayList<String>();
		for (int i = 0; i < getRegisteredAdapters().size(); i++) {
			IAdapter adapter = getRegisteredAdapter(i);
			// add the adapterName if it is started.
			if (adapter.getRunState().equals(RunStateEnum.STARTED)) {
				startedAdapters.add(adapter.getName());
			}
		}
		Collections.sort(startedAdapters, String.CASE_INSENSITIVE_ORDER);
		return startedAdapters;
	}

	public IAdapterService getAdapterService() {
		return adapterService;
	}

	@Autowired
	public void setAdapterService(IAdapterService adapterService) {
		this.adapterService = adapterService;
	}

	public void addStartAdapterThread(Runnable runnable) {
		startAdapterThreads.add(runnable);
	}

	public void removeStartAdapterThread(Runnable runnable) {
		startAdapterThreads.remove(runnable);
	}

	public List<Runnable> getStartAdapterThreads() {
		return startAdapterThreads;
	}

	public void addStopAdapterThread(Runnable runnable) {
		stopAdapterThreads.add(runnable);
	}

	public void removeStopAdapterThread(Runnable runnable) {
		stopAdapterThreads.remove(runnable);
	}

	public List<Runnable> getStopAdapterThreads() {
		return stopAdapterThreads;
	}

	public boolean isUnloadInProgressOrDone() {
		return unloadInProgressOrDone;
	}

	public void setUnloadInProgressOrDone(boolean unloadInProgressOrDone) {
		this.unloadInProgressOrDone = unloadInProgressOrDone;
	}

	/**
	 * Performs a check to see if the receiver is known at the adapter
	 * @return true if the receiver is known at the adapter
	 */
	public boolean isRegisteredReceiver(String adapterName, String receiverName) {
		IAdapter adapter = getRegisteredAdapter(adapterName);
		if(null == adapter) {
			return false;
		}
		return adapter.getReceiverByName(receiverName) != null;
	}

	/**
	 * Register an adapter with the configuration.
	 */
	public void registerAdapter(Adapter adapter) {
		if (!adapter.isActive()) {
			log.debug("adapter [" + adapter.getName() + "] is not active, therefore not included in configuration");
			return;
		}
		adapter.setConfiguration(this);

		try {
			adapterService.registerAdapter(adapter);
		} catch (ConfigurationException e) { //For some reason the adapterService configures the adapter...
			//TODO: this the configuration should have a configure method which configures every adapter.

			//Do nothing as this will cause the digester to stop digesting the configuration
			log.error("error configuring adapter ["+adapter.getName()+"]", e);
		}

		log.debug("Configuration [" + getName() + "] registered adapter [" + adapter.toString() + "]");
	}

	/**
	 * Register an {@link JobDef job} for scheduling at the configuration.
	 * The configuration will create an {@link JobDef AdapterJob} instance and a JobDetail with the
	 * information from the parameters, after checking the
	 * parameters of the job. (basically, it checks wether the adapter and the
	 * receiver are registered.
	 * <p>See the <a href="http://quartz.sourceforge.net">Quartz scheduler</a> documentation</p>
	 * @param jobdef a JobDef object
	 * @see nl.nn.adapterframework.scheduler.JobDef for a description of Cron triggers
	 * @since 4.0
	 */
	public void registerScheduledJob(JobDef jobdef) throws ConfigurationException {
		jobdef.configure(this);
		jobTable.put(jobdef.getName(), jobdef);
		scheduledJobs.add(jobdef);
	}

	public void registerStatisticsHandler(StatisticsKeeperIterationHandler handler) throws ConfigurationException {
		log.debug("registerStatisticsHandler() registering ["+ClassUtils.nameOf(handler)+"]");
		statisticsHandler=handler;
		handler.configure();
	}

	@Override
	public void setName(String name) {
		setId(name);
	}

	@Override
	public String getName() {
		return getId();
	}

	public void setVersion(String version) {
		if(StringUtils.isNotEmpty(version)) {
			this.version = version;
		}
	}

	public String getVersion() {
		return version;
	}

	/**
	 * If no ClassLoader has been set it tries to fall back on the `configurations.xxx.classLoaderType` property.
	 * Because of this, it may not always represent the correct or accurate type.
	 */
	public String getClassLoaderType() {
		if(!(getClassLoader() instanceof IConfigurationClassLoader)) { //Configuration has not been loaded yet
			String type = AppConstants.getInstance().getProperty("configurations."+getName()+".classLoaderType");
			if(StringUtils.isNotEmpty(type)) { //We may not return an empty String
				return type;
			} else {
				return null;
			}
		}

		return getClassLoader().getClass().getSimpleName();
	}

	@Autowired
	public void setIbisManager(IbisManager ibisManager) {
		ibisManager.addConfiguration(this);
		this.ibisManager = ibisManager;
	}

	public IbisManager getIbisManager() {
		return ibisManager;
	}


	public void setOriginalConfiguration(String originalConfiguration) {
		this.originalConfiguration = originalConfiguration;
	}

	public String getOriginalConfiguration() {
		return originalConfiguration;
	}

	public void setLoadedConfiguration(String loadedConfiguration) {
		this.loadedConfiguration = loadedConfiguration;
	}

	public String getLoadedConfiguration() {
		return loadedConfiguration;
	}

	public JobDef getScheduledJob(String name) {
		return jobTable.get(name);
	}

	public JobDef getScheduledJob(int index) {
		return scheduledJobs.get(index);
	}

	public List<JobDef> getScheduledJobs() {
		return scheduledJobs;
	}

	public void setConfigurationException(ConfigurationException exception) {
		configurationException = exception;
	}

	public ConfigurationException getConfigurationException() {
		return configurationException;
	}

	public BaseConfigurationWarnings getConfigurationWarnings() {
		return configurationWarnings;
	}

	@Override
	public ClassLoader getConfigurationClassLoader() {
		return getClassLoader();
	}
}
