/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportCourseMapHandler.java $
 * $Id: ImportCourseMapHandler.java 9693 2014-12-26 21:59:09Z ggolden $
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.coursemap.api.CourseMapItem;
import org.etudes.coursemap.api.CourseMapItemType;
import org.etudes.coursemap.api.CourseMapMap;
import org.etudes.coursemap.api.CourseMapService;

/**
 * Archives import handler for Coursemap
 */
public class ImportCourseMapHandler implements ImportHandler
{
	/** The application Id. */
	protected final static String applicationId = "sakai.coursemap";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportCourseMapHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: CourseMapService. */
	protected CourseMapService courseMapService = null;

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

		// get the source map - in the artifact
		List<Map<String, Object>> items = (List<Map<String, Object>>) artifact.getProperties().get("items");

		// get the destination map
		CourseMapMap dest = this.courseMapService.getMapEdit(siteId, null);

		// set mastery level
		Integer masteryLevel = (Integer) artifact.getProperties().get("masteryLevel");
		if (masteryLevel != null) dest.setMasteryPercent(masteryLevel);
		Boolean clearBlockOnClose = (Boolean) artifact.getProperties().get("clearBlockOnClose");
		if (clearBlockOnClose != null) dest.setClearBlockOnClose(clearBlockOnClose);

		// run through the source items, backwards
		for (int i = items.size() - 1; i >= 0; i--)
		{
			Map<String, Object> itemMap = items.get(i);

			// find a matching item (having the same title and type/application code) in the dest map
			String title = (String) itemMap.get("title");
			Integer appCode = (Integer) itemMap.get("appCode");
			Boolean blocker = (Boolean) itemMap.get("blocker");

			CourseMapItem matchingItemInDest = dest.getItem(title, appCode);
			if (matchingItemInDest != null)
			{
				// promote to be the new top
				matchingItemInDest.setMapPositioning(dest.getItems().get(0).getMapId());

				// match blocker setting
				matchingItemInDest.setBlocker(blocker);
			}

			// if not found, and the source item is a header, add it to the top
			else if (appCode.equals(CourseMapItemType.header.getAppCode()))
			{
				// add it up top
				CourseMapItem newHeader = dest.addHeaderBefore(null);
				newHeader.setTitle(title);
			}
		}

		// save the dest map
		this.courseMapService.saveMap(dest);
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
