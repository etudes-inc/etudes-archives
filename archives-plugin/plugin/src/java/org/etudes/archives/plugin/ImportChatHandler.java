/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/branches/ETU-237/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportChatHandler.java $
 * $Id: ImportChatHandler.java 5209 2013-06-17 00:50:16Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2014 Etudes, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.chat.api.ChatChannelEdit;
import org.sakaiproject.chat.cover.ChatService;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.TimeService;

/**
 * Archives import handler for Announcements
 */
public class ImportChatHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportChatHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.chat";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

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

		// site's channel is /chat/channel/<site id>/main
		String channelRef = "/chat/channel/" + siteId + "/";
		
		List to_channel_list = ChatService.getChannels("/chat/channel/" + siteId);
		int position = ChatService.getMaxPosition(to_channel_list) + 1;


		if (artifact.getReference().startsWith("/prefs"))
		{
			String paramFilterType = null, paramFilterParam = null;
			if (artifact.getProperties().get("paramFilterType") != null) paramFilterType = (String) artifact.getProperties().get("paramFilterType");
			if (artifact.getProperties().get("paramFilterParam") != null)
				paramFilterParam = (String) artifact.getProperties().get("paramFilterParam");

			try
			{
				Site site = this.siteService.getSite(siteId);
				ToolConfiguration chatTool = site.getToolForCommonId("sakai.chat");
				if (chatTool != null)
				{
					// chatTool.getConfig().removeProperty("channel");
					if (paramFilterType != null && chatTool.getPlacementConfig().getProperty("PARAM_FILTER_TYPE") == null)
						chatTool.getPlacementConfig().setProperty("PARAM_FILTER_TYPE", paramFilterType);
					if (paramFilterParam != null && chatTool.getPlacementConfig().getProperty("PARAM_FILTER_PARAM") == null)
						chatTool.getPlacementConfig().setProperty("PARAM_FILTER_PARAM", paramFilterParam);

					chatTool.save();
				}
			}
			catch (IdUnusedException e3)
			{
				M_log.warn("importArtifact: missing site: " + siteId);
			}

		}
		else
		{
			String title = (String) artifact.getProperties().get("title");

			ChatChannelEdit channel = null;
			try
			{
				channel = (ChatChannelEdit) ChatService.editChannel(channelRef + title);
			}
			catch (IdUnusedException e)
			{
				try
				{
					// create the channel
					channel = ChatService.addChatChannel(channelRef + title);
				}
				catch (IdInvalidException e2)
				{
					M_log.warn("importArtifact: " + e.toString());
				}
				catch (IdUsedException e2)
				{
					M_log.warn("importArtifact: " + e.toString());
				}
				catch (PermissionException e2)
				{
					M_log.warn("importArtifact: " + e.toString());
				}

				if (channel == null)
				{
					M_log.warn("importArtifact: cannot find or create channel: " + channelRef + title);
				}

				/*
				 * String position = (String) artifact.getProperties().get("position"); if (position != null) channel.getPropertiesEdit().addProperty("position", position);
				 */

				channel.getPropertiesEdit().addProperty("published", "0");
				channel.getPropertiesEdit().addProperty("position", Integer.toString(position));

				List<String> groupTitles = (List<String>) artifact.getProperties().get("groups");
				List<String> sectionTitles = (List<String>) artifact.getProperties().get("sections");
				List<String> combined = new ArrayList<String>();
				if (groupTitles != null) combined.addAll(groupTitles);
				if (sectionTitles != null) combined.addAll(sectionTitles);
				if (!combined.isEmpty())
				{
					try
					{
						Site s = this.siteService.getSite(siteId);
						Collection<Group> groups = (Collection<Group>) s.getGroups();

						Set<Group> chatGroups = new HashSet<Group>();
						for (String groupTitle : combined)
						{
							for (Group g : groups)
							{
								if (g.getTitle().equals(groupTitle))
								{
									chatGroups.add(g);
									break;
								}
							}
						}

						if (!chatGroups.isEmpty())
						{
							channel.getChatChannelHeaderEdit().setGroupAccess(chatGroups);
						}
					}
					catch (IdUnusedException e3)
					{
						M_log.warn("importArtifact: missing site: " + siteId);
					}
					catch (PermissionException e3)
					{
						M_log.warn("importArtifact: " + e.toString());
					}
				}
			}
			catch (InUseException e)
			{
				M_log.warn("importArtifact: " + e.toString());
			}
			catch (PermissionException e)
			{
				M_log.warn("importArtifact: " + e.toString());
			}
			ChatService.commitChannel(channel);
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
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		// if importing, add the references
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
}
