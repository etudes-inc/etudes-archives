/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportWebContentHandler.java $
 * $Id: ImportWebContentHandler.java 8753 2014-09-11 17:39:31Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014 Etudes, Inc.
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

import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolManager;

/**
 * Archives import handler for the Archives (site) info.
 */
public class ImportWebContentHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportWebContentHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.iframe";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** the tool id we are interested in for checking for existing entries not to duplicate. */
	protected String[] ourToolIds =
	{ "sakai.iframe" };

	/** Dependency: SiteService */
	protected SiteService siteService = null;

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
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		// get the Test Center tool
		Tool iframeTool = toolManager.getTool("sakai.iframe");

		// get the site
		try
		{
			Site site = this.siteService.getSite(siteId);

			String toolTitle = (String) artifact.getProperties().get("toolTitle");
			String height = (String) artifact.getProperties().get("height");
			String source = (String) artifact.getProperties().get("source");
			String key = (String) artifact.getProperties().get("key");
			String secret = (String) artifact.getProperties().get("secret");
			String extraInformation = (String) artifact.getProperties().get("extraInformation");
			String thirdPartyService = (String) artifact.getProperties().get("thirdPartyService");
			Boolean newPage = (Boolean) artifact.getProperties().get("newPage");
			String pageTitle = (String) artifact.getProperties().get("pageTitle");

			if ("Yes".equalsIgnoreCase(thirdPartyService)) return;
			// do not import items that already exist or if content url is null - check against the site's sakai.iframe tool entries
			List<ToolConfiguration> tools = (List<ToolConfiguration>) site.getTools(ourToolIds);
			boolean found = false;
			for (ToolConfiguration tool : tools)
			{
				if (source.equalsIgnoreCase(tool.getPlacementConfig().getProperty("source")))
				{
					found = true;
					break;
				}
			}

			if (!found)
			{				
				SitePage page = site.addPage();
				page.setTitle(pageTitle);
				page.setPopup(newPage);
				ToolConfiguration tool = page.addTool();
				tool.setTool("sakai.iframe", iframeTool);
				tool.setTitle(toolTitle);
				tool.getPlacementConfig().put("height", height);
				tool.getPlacementConfig().put("source", source);
				tool.getPlacementConfig().put("key", (key != null) ? key : "");
				tool.getPlacementConfig().put("secret", (secret != null) ? secret : "");
				tool.getPlacementConfig().put("extraInformation", (extraInformation != null) ? extraInformation : "");
				tool.getPlacementConfig().setProperty("thirdPartyService", (thirdPartyService != null) ? thirdPartyService : "No");		
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
