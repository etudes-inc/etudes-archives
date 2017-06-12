/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportArchiveHandler.java $
 * $Id: ImportArchiveHandler.java 8761 2014-09-11 22:57:00Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2012 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.archives.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.PubDatesService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolManager;

/**
 * Archives import handler for the Archives (site) info.
 */
public class ImportArchiveHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportArchiveHandler.class);

	/** The application Id. */
	protected final static String applicationId = "etudes.archives";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: PubDatesService */
	protected PubDatesService pubDatesService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** Dependency: TimeService. */
	protected TimeService timeService = null;
	
	/** The tool manager. */
	protected ToolManager toolManager = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterImportHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		M_log.info("import " + applicationId + " in site: " + siteId);

		// get the site
		try
		{
			Site site = this.siteService.getSite(siteId);
			boolean changed = false;

			// import site info, as used by homepage?
			if ((toolIds == null) || (toolIds.contains("e3.configure")) || (toolIds.contains("e3.homepage")))
			{
				// set the descriptions only if the site has none already set
				if (site.getDescription() == null)
				{
					String description = (String) artifact.getProperties().get("siteDescription");
					if (description != null)
					{
						site.setDescription(description);
						changed = true;
					}
				}

				// info url (for the "Worksite Information" on the home page
				if (site.getInfoUrl() == null)
				{
					String infoUrl = (String) artifact.getProperties().get("infoUrl");
					// null is an ok setting
					site.setInfoUrl(infoUrl);
					changed = true;
				}
			}

			// import the rest of site info?
			if ((toolIds == null) || (toolIds.contains("e3.configure")))
			{
				// skin - only if the site's skin has not been set by the user
				if (!site.userEditedSkin())
				{
					String iconUrl = (String) artifact.getProperties().get("iconUrl");
					String skin = (String) artifact.getProperties().get("skin");
					if ((skin != null) && (iconUrl != null))
					{
						site.setIconUrl(iconUrl);
						site.setSkin(skin);
						changed = true;
					}
				}

				// publish / unpublish dates - only if not set already
				if (!site.userEditedPubDates())
				{
					Time pubDate = null;
					Time unpubDate = null;
					Long pubDateL = (Long) artifact.getProperties().get("pubDate");
					if (pubDateL != null) pubDate = this.timeService.newTime(pubDateL.longValue());
					Long unpubDateL = (Long) artifact.getProperties().get("unpubDate");
					if (unpubDateL != null) unpubDate = this.timeService.newTime(unpubDateL.longValue());
					this.pubDatesService.processPublishOptions("setdates", pubDate, unpubDate, site, null);
				}

				// import external services - if not in site
				List<Map<String, Object>> party3Collection = (List<Map<String, Object>>) artifact.getProperties().get("externalServices");
				if (party3Collection != null)
				{
					// do not import items that already exist or if content url is null - check against the site's sakai.iframe tool entries
					List<ToolConfiguration> tools = (List<ToolConfiguration>) site.getTools("sakai.iframe");
					Tool iframeTool = toolManager.getTool("sakai.iframe");

					for (Map<String, Object> party3Map : party3Collection)
					{
						String source = (String) party3Map.get("source");
						String thirdPartyService = (String) party3Map.get("thirdPartyService");
						boolean found = false;
						for (ToolConfiguration tool : tools)
						{
							if (source.equalsIgnoreCase(tool.getPlacementConfig().getProperty("source"))
									&& thirdPartyService.equalsIgnoreCase(tool.getPlacementConfig().getProperty("thirdPartyService")))
							{
								found = true;
								break;
							}
						}

						if (!found)
						{
							SitePage page = site.addPage();
							page.setTitle((String) party3Map.get("pageTitle"));
							page.setPopup((Boolean) party3Map.get("newPage"));
							ToolConfiguration tool = page.addTool();
							tool.setTool("sakai.iframe", iframeTool);
							tool.setTitle((String) party3Map.get("toolTitle"));
							tool.getPlacementConfig().put("height", (String) party3Map.get("height"));
							tool.getPlacementConfig().put("source", source);
							tool.getPlacementConfig().put("key", (party3Map.get("key") != null ? (String) party3Map.get("key") : ""));
							tool.getPlacementConfig().put("secret", (party3Map.get("secret") != null ? (String) party3Map.get("secret") : ""));
							tool.getPlacementConfig().put("extraInformation",
									(party3Map.get("extraInformation") != null ? (String) party3Map.get("extraInformation") : ""));
							tool.getPlacementConfig().setProperty("thirdPartyService", (String) party3Map.get("thirdPartyService"));
							changed = true;
						}
					} // party3collection
				}
			}

			// import site groups?
			if ((toolIds == null) || (toolIds.contains("e3.siteroster")))
			{
				List<Map<String, Object>> groupsCollection = (List<Map<String, Object>>) artifact.getProperties().get("groups");
				if (groupsCollection != null)
				{
					for (Map<String, Object> groupMap : groupsCollection)
					{
						String gTitle = (String) groupMap.get("title");

						// do we have a group by this title?
						boolean found = false;
						Collection<Group> groups = (Collection<Group>) site.getGroups();
						for (Group g : groups)
						{
							if (g.getTitle().equals(gTitle))
							{
								found = true;
								break;
							}
						}

						// create the group if we don't already have one
						if (!found)
						{
							Group g = site.addGroup();

							// so it shows up in worksite setup
							g.getProperties().addProperty("group_prop_wsetup_created", Boolean.TRUE.toString());

							g.setTitle(gTitle);
							String gDescription = (String) groupMap.get("description");
							g.setDescription(gDescription);

							changed = true;
						}
					}
				}
			}

			if (changed)
			{
				this.siteService.save(site);
			}
		}
		catch (IdUnusedException e)
		{
			M_log.warn("importArtifact: missing site: " + siteId);
		}
		catch (PermissionException e)
		{
			M_log.warn("importArtifact: in site: " + siteId + e.toString());
		}
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerImportHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerFilteredReferences(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		archive.getReferences().addAll(artifact.getReferences());
	}

	/**
	 * Set the archives service.
	 * 
	 * @param service
	 *        The archives service.
	 */
	public void setArchivesService(ArchivesService service)
	{
		this.archivesService = service;
	}

	/**
	 * Dependency: PubDatesService.
	 * 
	 * @param service
	 *        The PubDatesService.
	 */
	public void setPubDatesService(PubDatesService service)
	{
		this.pubDatesService = service;
	}

	/**
	 * Dependency: SiteService.
	 * 
	 * @param service
	 *        The SiteService.
	 */
	public void setSiteService(SiteService service)
	{
		this.siteService = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Set the TimeService.
	 * 
	 * @param service
	 *        The TimeService.
	 */
	public void setTimeService(TimeService service)
	{
		this.timeService = service;
	}
	
	/**
	 * Set the tool manager.
	 * 
	 * @param service
	 *        The tool manager.
	 */
	public void setToolManager(ToolManager service)
	{
		this.toolManager = service;
	}
}
