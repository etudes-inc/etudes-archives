/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/branches/ETU-237/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveChatHandler.java $
 * $Id: ArchiveChatHandler.java 5209 2013-06-17 00:50:16Z ggolden $
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.XrefHelper;
import org.sakaiproject.chat.api.ChatChannel;
import org.sakaiproject.chat.cover.ChatService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.message.api.MessageChannel;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.util.StringUtil;

/**
 * Archives archive handler for Chat
 */
public class ArchiveChatHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveChatHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.chat";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;
	
	/** Dependency: SiteService */
	protected SiteService siteService = null;	
	
	private static final String PARAM_FILTER_TYPE = "filter-type";

	private static final String PARAM_FILTER_PARAM = "filter-param";

	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		List channel_list = ChatService.getChannels("/chat/channel/"+siteId);
	
		channel_list = ChatService.sortByPosition(channel_list);
		
		Iterator itr = channel_list.iterator();
		List site_channel_list = new Vector();

		String channelId = null;
		try
		{
			Site site = this.siteService.getSite(siteId);
			ToolConfiguration chatTool = site.getToolForCommonId("sakai.chat");
			if (chatTool != null)
			{
				if (chatTool.getConfig().getProperty(PARAM_FILTER_TYPE) != null || chatTool.getConfig().getProperty(PARAM_FILTER_PARAM) != null)
				{
					Artifact artifact = archive.newArtifact(applicationId, "/prefs");

					if (chatTool.getConfig().getProperty(PARAM_FILTER_TYPE) != null)
						artifact.getProperties().put("paramFilterType", chatTool.getConfig().getProperty(PARAM_FILTER_TYPE));

					if (chatTool.getConfig().getProperty(PARAM_FILTER_PARAM) != null)
						artifact.getProperties().put("paramFilterParam", chatTool.getConfig().getProperty(PARAM_FILTER_PARAM));

					// archive it
					archive.archive(artifact);
				}
				
			}
		}
		catch (IdUnusedException e)
		{
		}

		while (itr.hasNext())
		{
			ChatChannel channel = (ChatChannel) itr.next();

			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, channel.getId());

			artifact.getProperties().put("title", channel.getId());
			if (channel.getChatHeader() != null && channel.getChatHeader().getAccess() != null)
				artifact.getProperties().put("access", channel.getChatHeader().getAccess().toString());
			/*
			 * String position = channel.getProperties().getProperty("position"); if (position != null) artifact.getProperties().put("position", position);
			 */
			
			if (channel.getChatHeader() != null && channel.getChatHeader().getGroupObjects() != null)
			{
				List<String> groupTitles = new ArrayList<String>();
				List<String> sectionTitles = new ArrayList<String>();
				Collection<Group> groups = channel.getChatHeader().getGroupObjects();
				for (Group group : groups)
				{
					// for groups
					if (group.getProperties().getProperty("sections_category") == null)
					{
						groupTitles.add(group.getTitle());
					}

					// for sections
					else
					{
						sectionTitles.add(group.getTitle());
					}
				}
				if (!groupTitles.isEmpty()) artifact.getProperties().put("groups", groupTitles);
				if (!sectionTitles.isEmpty()) artifact.getProperties().put("sections", sectionTitles);
			}
			// archive it
			archive.archive(artifact);

		}

	}

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterArchiveHandler(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public String getApplicationId()
	{
		return applicationId;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerArchiveHandler(this);
		M_log.info("init()");
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
