/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveCourseMapHandler.java $
 * $Id: ArchiveCourseMapHandler.java 9693 2014-12-26 21:59:09Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2014 Etudes, Inc.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.coursemap.api.CourseMapItem;
import org.etudes.coursemap.api.CourseMapMap;
import org.etudes.coursemap.api.CourseMapService;

/**
 * Archives archive handler for Coursemap
 */
public class ArchiveCourseMapHandler implements ArchiveHandler
{
	/** The application Id. */
	protected final static String applicationId = "sakai.coursemap";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveCourseMapHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: CourseMapService. */
	protected CourseMapService courseMapService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// make an artifact
		boolean used = false;
		Artifact artifact = archive.newArtifact(applicationId, "/coursemap");

		// get the map
		CourseMapMap source = this.courseMapService.getMapEdit(siteId, null);

		// mastery level
		if (source.getMasteryPercent() != null)
		{
			artifact.getProperties().put("masteryLevel", source.getMasteryPercent());
			artifact.getProperties().put("clearBlockOnClose", source.getClearBlockOnClose());
			used = true;
		}

		// items
		List<Map<String, Object>> itemsCollection = new ArrayList<Map<String, Object>>();
		artifact.getProperties().put("items", itemsCollection);
		for (CourseMapItem item : source.getItems())
		{
			Map<String, Object> itemMap = new HashMap<String, Object>();
			itemsCollection.add(itemMap);

			itemMap.put("title", item.getTitle());
			itemMap.put("appCode", item.getType().getAppCode());
			itemMap.put("blocker", item.getBlocker());
			used = true;
		}

		// archive it if used
		if (used)
		{
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
	 * Set the CourseMapService.
	 * 
	 * @param service
	 *        The CourseMapService.
	 */
	public void setCourseMapService(CourseMapService service)
	{
		this.courseMapService = service;
	}
}
