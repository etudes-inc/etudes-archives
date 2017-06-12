/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/tags/1.10/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveGradebookHandler.java $
 * $Id: ArchiveGradebookHandler.java 5210 2013-06-17 16:37:00Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2015 Etudes, Inc.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.etudes.gradebook.api.*;

/**
 * Archives archive handler for Gradebook
 */
public class ArchiveE3GradebookHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveE3GradebookHandler.class);

	/** The application Id. */
	protected final static String applicationId = "e3.gradebook";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: GradebookService. */
	protected GradebookService gradebookService = null;
	
	protected GradebookImportService gradebookImportService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);
		try
		{
			boolean fromContextGB = gradebookService.isToolAvailable(siteId);
			if (!fromContextGB) return;
			
			String modifiedByUserId = UserDirectoryService.getCurrentUser().getId();
			Gradebook gb = gradebookService.getContextGradebook(siteId, modifiedByUserId);
						
			//options
			Artifact optionsArtifact = archive.newArtifact(applicationId, "/gradingOptions");
			optionsArtifact.getProperties().put("defaultGradingScale", gb.getGradingScale().getScaleCode());
			
			if (gb.getBoostUserGradesBy() != null)
			{
				optionsArtifact.getProperties().put("booster", gb.getBoostUserGradesBy());
				optionsArtifact.getProperties().put("boosterType", gb.getBoostUserGradesType().getCode());
			}
			optionsArtifact.getProperties().put("showLetterGrade",gb.isShowLetterGrade());
			optionsArtifact.getProperties().put("releaseGrades",gb.getReleaseGrades().getCode());
			
			// letter scales
			List<GradingScale> toScales = gb.getContextGradingScales();
			optionsArtifact.getProperties().put("gradingScalesCount", toScales.size());
		
			for (GradingScale gs : toScales)
			{
				List<GradingScalePercent> fromScalePercent = gs.getGradingScalePercent();
				String sc = gs.getScaleCode();
				int gsIdx = 1;
				for (GradingScalePercent p : fromScalePercent)
				{
					optionsArtifact.getProperties().put("gradingScalePercent_" + sc + "_"+ gsIdx + "_letter", p.getLetterGrade());
					optionsArtifact.getProperties().put("gradingScalePercent_" + sc + "_"+ gsIdx + "_letterP", p.getPercent());
					gsIdx++;
				}
				optionsArtifact.getProperties().put("gradingScalePercentCount_" + sc, fromScalePercent.size());				
			}

			//categories
			int categoriesCount = 1;
			optionsArtifact.getProperties().put("categoriesType", gb.getCategoryType().getCode());
			List<GradebookCategory> allCategories = gradebookImportService.findE3ContextCategories(gb.getId());
			optionsArtifact.getProperties().put("categoryCount", allCategories.size());
		
			for (GradebookCategory gc : allCategories)
			{
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_title", gc.getTitle());
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_weight", (gc.getWeight() != null) ? gc.getWeight() : null);
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_weightDistribution", (gc.getWeightDistribution() != null) ? gc.getWeightDistribution().getCode() : "");
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_lowestDrop", gc.getDropNumberLowestScores());
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_type", gc.getCategoryType().getCode());
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_code", gc.getStandardCategoryCode());
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_order", gc.getOrder());
				optionsArtifact.getProperties().put("category_" + categoriesCount + "_id", gc.getId());
				categoriesCount++;
			}
			archive.archive(optionsArtifact);
			
			//item mapping
			List<GradebookItem> fromGradebookItems = gradebookService.getImportGradebookItems(siteId, modifiedByUserId, false, true, null, true, null);
			List<GradebookCategoryItemMap> fromItemsMap = gradebookImportService.findItemMapping(siteId);
			if (fromItemsMap == null || fromItemsMap.size() == 0) return;
			
			List<GradebookCategoryItemMap> standardItemMap = new ArrayList<GradebookCategoryItemMap>();
			List<GradebookCategoryItemMap> customItemMap = new ArrayList<GradebookCategoryItemMap>();	
			
			for (GradebookCategoryItemMap item : fromItemsMap)
			{
				GradebookCategory category = gradebookImportService.findExistingCategory(item.getCategoryId(), allCategories);
				if (category == null) continue;
				// if to site doesn't have itemId - categoryId combination then add
				if (category.getCategoryType().getCode() == 1 && !(standardItemMap.contains(item)))
					standardItemMap.add(item);
				
				if (category.getCategoryType().getCode() == 2 && !(customItemMap.contains(item)))
					customItemMap.add(item);	
			}	
			
			// archive standard type mapping
			Artifact standardCategoryItemOrderArtifact = archive.newArtifact(applicationId, "/standardGradingCategoryItemOrder");
			int itemCount = 1;
			Collections.sort(standardItemMap, new GradebookCategoryItemMapComparator(true));
			standardCategoryItemOrderArtifact.getProperties().put("itemCount", standardItemMap.size());
			for (GradebookCategoryItemMap item : standardItemMap)
			{
				GradebookItem gitem = gradebookImportService.findGradebookItem(item.getItemId(), fromGradebookItems);
				GradebookCategory category = gradebookImportService.findExistingCategory(item.getCategoryId(), allCategories);
				if (gitem == null || category == null) continue;
				standardCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_categoryTitle", category.getTitle());
				standardCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_categoryCode", category.getStandardCategoryCode());
				standardCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_title", gitem.getTitle());
				standardCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_type", gitem.getType().getDisplayString());
				itemCount++;
			}			
			archive.archive(standardCategoryItemOrderArtifact);
			
			// archive custom mapping
			Artifact customCategoryItemOrderArtifact = archive.newArtifact(applicationId, "/customGradingCategoryItemOrder");
			itemCount = 1;
			Collections.sort(customItemMap, new GradebookCategoryItemMapComparator(true));
			customCategoryItemOrderArtifact.getProperties().put("itemCount", customItemMap.size());
			for (GradebookCategoryItemMap item : customItemMap)
			{
				GradebookItem gitem = gradebookImportService.findGradebookItem(item.getItemId(), fromGradebookItems);
				GradebookCategory category = gradebookImportService.findExistingCategory(item.getCategoryId(), allCategories);
				if (gitem == null || category == null) continue;
				customCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_categoryTitle", category.getTitle());
				customCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_categoryCode", category.getStandardCategoryCode());
				customCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_title", gitem.getTitle());
				customCategoryItemOrderArtifact.getProperties().put("item_" + itemCount + "_type", gitem.getType().getDisplayString());
				itemCount++;
			}			
			archive.archive(customCategoryItemOrderArtifact);
		}
		catch (Exception e)
		{
			M_log.info(e.getMessage());
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
	 * Set the gradebook service.
	 * 
	 * @param service
	 *        The gradebook service.
	 */
	public void setGradebookService(GradebookService gradebookService)
	{
		this.gradebookService = gradebookService;
	}

	/**
	 * Set the gradebook service.
	 * 
	 * @param service
	 *        The gradebook service.
	 */
	public void setGradebookImportService(GradebookImportService gradebookImportService)
	{
		this.gradebookImportService = gradebookImportService;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}
}
