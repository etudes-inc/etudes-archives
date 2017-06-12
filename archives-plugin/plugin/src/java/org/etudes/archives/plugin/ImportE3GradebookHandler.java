/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/tags/1.10/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportGradebookHandler.java $
 * $Id: ImportGradebookHandler.java 9579 2014-12-18 03:30:07Z ggolden $
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.sakaiproject.db.api.SqlService;
import org.etudes.gradebook.api.*;
import org.etudes.gradebook.api.GradebookCategory.WeightDistribution;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.cover.UserDirectoryService;

/**
 * Archives import handler for Gradebook
 */
public class ImportE3GradebookHandler implements ImportHandler
{
	class archivedGradebookItem
	{		
		String title;
		String toolTitle;
		public archivedGradebookItem(String title2, String typeDisplayString) 
		{
			title = title2;
			toolTitle = typeDisplayString;
		}
	}

	/** The application Id. */
	protected final static String applicationId = "e3.gradebook";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportE3GradebookHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: GradebookService. */
	protected GradebookService gradebookService = null;

	/** Dependency: GradebookImportService. */
	protected GradebookImportService gradebookImportService = null;
	
	/** Dependency: SiteService. */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterImportHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Find corresponding gradeable item with same title and type.
	 * @param title
	 * @param typeDisplayString
	 * @param siteGradableItems
	 * @return
	 */
	protected archivedGradebookItem findMatchingArchiveItem(String title, String typeDisplayString, List<archivedGradebookItem> archiveGradableItems)
	{
		for (archivedGradebookItem item : archiveGradableItems)
		{
			if (item.title.equals(title) && item.toolTitle.equals(typeDisplayString)) return item;
		}
		return null;
	}
	
	/**
	 * Find corresponding gradeable item with same title and type.
	 * @param title
	 * @param typeDisplayString
	 * @param siteGradableItems
	 * @return
	 */
	protected GradebookItem findMatchingItem(String title, String typeDisplayString, List<GradebookItem> siteGradableItems)
	{
		for (GradebookItem item : siteGradableItems)
		{
			if (item.getTitle().equals(title) && item.getType().getDisplayString().equals(typeDisplayString)) return item;
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);
		try
		{			
			String modifiedByUserId = UserDirectoryService.getCurrentUser().getId();
			Gradebook gb = gradebookService.getContextGradebook(siteId, modifiedByUserId);
			if (gb == null) return;
			
			if (artifact.getReference().equals("/gradingOptions"))
			{
				importGradingOptions(artifact, gb, siteId, modifiedByUserId);
			}		
			
			// import default selected type mapping - old style
			if (artifact.getReference().equals("/gradingCategoryItemOrder"))
			{
				importCategoryItemMapping(artifact, gb, siteId, modifiedByUserId);
			}	
			
			// import both standard and custom mapping
			if (artifact.getReference().equals("/standardGradingCategoryItemOrder"))
			{
				importCategoryItemMapping(artifact, gb, siteId, Gradebook.CategoryType.Standard, modifiedByUserId);
			}	
			
			if (artifact.getReference().equals("/customGradingCategoryItemOrder"))
			{
				importCategoryItemMapping(artifact, gb, siteId, Gradebook.CategoryType.Custom, modifiedByUserId);
			}	
			
		}
		catch(Exception e)
		{
			M_log.info(e.getMessage());
		}
	}
	
	/**
	 * 
	 * @param artifact
	 * @return
	 */
	List<archivedGradebookItem> findAllArchivedItems(Artifact artifact)
	{
		List<archivedGradebookItem> allArchived = new ArrayList<archivedGradebookItem>();
		Integer categoryCount = (Integer) artifact.getProperties().get("categoryCount");
		
		for (int count=1; count <= categoryCount; count++)
		{
			Integer categoryId = (Integer) artifact.getProperties().get("category_" + count + "_id");
			Integer itemsCount = (Integer)artifact.getProperties().get("itemCount_" + categoryId);
			if (itemsCount != null)
			{
				//read category items from archive
				for (int itemCount = 1; itemCount <= itemsCount; itemCount++)
				{
					String title = (String) artifact.getProperties().get("item_" + categoryId + "_" + itemCount + "_title");
					String typeDisplayString = (String) artifact.getProperties().get("item_" + categoryId + "_" + itemCount + "_type");
					if (title != null && typeDisplayString != null)
						allArchived.add(new archivedGradebookItem(title.trim(), typeDisplayString.trim()));
				}
			}
		}
		return allArchived;
	}
	
	/**
	 * Import grading options, grade booster and categories.
	 * @param artifact
	 * @param gb
	 * @param siteId
	 * @param modifiedByUserId
	 */
	protected void importCategoryItemMapping(Artifact artifact, Gradebook gb, String siteId, String modifiedByUserId)
	{
		Integer categoryCount = (Integer) artifact.getProperties().get("categoryCount");
		List<GradebookCategory> gradebookCategories = gradebookImportService.findE3ContextCategories(gb.getId());
		
		List<GradebookItem> siteGradebleItems = gradebookService.getImportGradebookItems(siteId, modifiedByUserId, false, true, null, true, null);
		List<GradebookCategoryItemMap> toItemsMap = gradebookImportService.findItemMapping(siteId);

		// associate item map elements with their category. Need for comparator below
		if (toItemsMap == null) toItemsMap = new ArrayList<GradebookCategoryItemMap>();
		for (GradebookCategoryItemMap associateCategory : toItemsMap)
		{
			GradebookCategory toCategory = gradebookImportService.findExistingCategory(associateCategory.getCategoryId(), gradebookCategories);
			if (toCategory == null) toItemsMap.remove(associateCategory);
			else associateCategory.setCategory(toCategory);		
		}
		
		int displayOrder = 1;
		Gradebook.CategoryType categoryType = Gradebook.CategoryType.Standard;
		for (int count=1; count <= categoryCount; count++)
		{
			//read categories from archive
			String categoryTitle = (String) artifact.getProperties().get("category_" + count + "_title");
			Integer type = (Integer) artifact.getProperties().get("category_" + count + "_type");
			categoryType = (new Integer(2).equals(type)) ? Gradebook.CategoryType.Custom : Gradebook.CategoryType.Standard;
			
			Integer categoryCode = (Integer) artifact.getProperties().get("category_" + count + "_code");
			Integer categoryId = (Integer) artifact.getProperties().get("category_" + count + "_id");
			
			// find matching in the site
			GradebookCategory category = gradebookImportService.findExistingCategory(categoryType, categoryCode, categoryTitle, gradebookCategories);
			if (category == null) continue;
			
			Integer itemsCount = (Integer)artifact.getProperties().get("itemCount_" + categoryId);
		
			if (itemsCount != null)
			{
				//read category items from archive
				for (int itemCount = 1; itemCount <= itemsCount; itemCount++)
				{
					String title = (String) artifact.getProperties().get("item_" + categoryId + "_" + itemCount + "_title");
					String typeDisplayString = (String) artifact.getProperties().get("item_" + categoryId + "_" + itemCount + "_type");
					if (title == null || typeDisplayString == null) continue;
					GradebookItem item = findMatchingItem(title.trim(), typeDisplayString.trim(), siteGradebleItems);
					if (item == null) continue;
					M_log.debug("category item associated" + item.getTitle() + ","+category.getId());
					GradebookCategoryItemMap gitem = gradebookService.newGradebookCategoryItemMap(item.getId(), category.getId(), displayOrder++); 
					gitem.setCategory(category);
					if (!toItemsMap.contains(gitem))toItemsMap.add(gitem);				
				}
			}		
		}
		
		//check display number or re-sort			
		Collections.sort(toItemsMap, new GradebookCategoryItemMapComparator(true));
	
		//create new map
		List<GradebookCategoryItemMap> itemMap = new ArrayList<GradebookCategoryItemMap>();
		int loopCount = 0;
		for (GradebookCategoryItemMap mapItem : toItemsMap)
		{
			int displayNumber = (mapItem.getDisplayOrder() == 0) ? 0 : ++loopCount;
			GradebookCategoryItemMap gitem = gradebookService.newGradebookCategoryItemMap(mapItem.getItemId(), mapItem.getCategoryId(), displayNumber);
			itemMap.add(gitem);
		}
		//save item map
		if (itemMap.size() > 0) gradebookService.modifyImportItemMapping(siteId, categoryType, itemMap, modifiedByUserId);
	}
	
	/**
	 * this is for new format - to bring in both standard and custom mapping
	 * @param artifact
	 * @param gb
	 * @param siteId
	 * @param modifiedByUserId
	 */
	protected void importCategoryItemMapping(Artifact artifact, Gradebook gb, String siteId, Gradebook.CategoryType categoryType, String modifiedByUserId)
	{
		Integer itemCount = (Integer) artifact.getProperties().get("itemCount");
		List<GradebookCategory> gradebookCategories = gradebookImportService.findE3ContextCategories(gb.getId());
		
		List<GradebookItem> siteGradebleItems = gradebookService.getImportGradebookItems(siteId, modifiedByUserId, false, true, null, true, null);
		List<GradebookCategoryItemMap> toItemsMap = gradebookImportService.findItemMapping(siteId);

		// associate item map elements with their category. Need for comparator below
		if (toItemsMap == null) toItemsMap = new ArrayList<GradebookCategoryItemMap>();
		List<GradebookCategoryItemMap> standardCustomItemMap = new ArrayList<GradebookCategoryItemMap>();
		
		for (GradebookCategoryItemMap associateCategory : toItemsMap)
		{
			GradebookCategory toCategory = gradebookImportService.findExistingCategory(associateCategory.getCategoryId(), gradebookCategories);
			if (toCategory == null) toItemsMap.remove(associateCategory);
			else 
			{
				associateCategory.setCategory(toCategory);		
				if (toCategory.getCategoryType().getCode() == categoryType.getCode() && !(standardCustomItemMap.contains(associateCategory)))
					standardCustomItemMap.add(associateCategory);
			}
		}
		
		int displayOrder = 1;
	
		for (int count=1; count <= itemCount; count++)
		{
			//read categories from archive
			String categoryTitle = (String) artifact.getProperties().get("item_" + count + "_categoryTitle");
			Integer categoryCode = (Integer) artifact.getProperties().get("item_" + count + "_categoryCode");
				
			// find matching in the site
			GradebookCategory category = gradebookImportService.findExistingCategory(categoryType, categoryCode, categoryTitle, gradebookCategories);
			if (category == null) continue;
			
			String title = (String) artifact.getProperties().get("item_" + count + "_title");
			String typeDisplayString = (String) artifact.getProperties().get("item_" + count + "_type");
			if (title == null || typeDisplayString == null) continue;
			
			GradebookItem item = findMatchingItem(title.trim(), typeDisplayString.trim(), siteGradebleItems);
			if (item == null) continue;
			M_log.debug("category item associated" + item.getTitle() + ","+category.getId());
			GradebookCategoryItemMap gitem = gradebookService.newGradebookCategoryItemMap(item.getId(), category.getId(), displayOrder++); 
			gitem.setCategory(category);
			
			if (category.getCategoryType().getCode() == categoryType.getCode() && !standardCustomItemMap.contains(gitem))
				standardCustomItemMap.add(gitem);	
		}
		//check display number or re-sort			
		Collections.sort(standardCustomItemMap, new GradebookCategoryItemMapComparator(true));
	
		//create standard new map
		List<GradebookCategoryItemMap> itemMap = new ArrayList<GradebookCategoryItemMap>();
		int loopCount = 0;
		for (GradebookCategoryItemMap mapItem : standardCustomItemMap)
		{
			int displayNumber = (mapItem.getDisplayOrder() == 0) ? 0 : ++loopCount;
			mapItem.setDisplayOrder(displayNumber);
			itemMap.add(mapItem);
		}
		if (itemMap.size() > 0) gradebookService.modifyImportItemMapping(siteId, categoryType, itemMap, modifiedByUserId);
	}
	
	/**
	 * Import grading options, grade booster and categories.
	 * @param artifact
	 * @param gb
	 * @param siteId
	 * @param modifiedByUserId
	 */
	protected void importGradingOptions(Artifact artifact, Gradebook gb, String siteId, String modifiedByUserId)
	{
		String defaultScale = (String) artifact.getProperties().get("defaultGradingScale");
		
		//import booster
		Float gradeBooster = (Float) artifact.getProperties().get("booster");
		Integer boosterType = (Integer) artifact.getProperties().get("boosterType");
					
		if (gradeBooster != null && boosterType != null)
		{
			Gradebook.BoostUserGradesType b = (new Integer(2).equals(boosterType)) ? Gradebook.BoostUserGradesType.percent : Gradebook.BoostUserGradesType.points;
			gradebookService.modifyContextGradebookBoostByAttributes(siteId, b, gradeBooster, modifiedByUserId);
		}
	
		Boolean showLetter = (Boolean) artifact.getProperties().get("showLetterGrade");
		Integer releaseGradeCode = (Integer) artifact.getProperties().get("releaseGrades");
		Gradebook.ReleaseGrades releaseGrade = (new Integer(1).equals(releaseGradeCode)) ? Gradebook.ReleaseGrades.All : Gradebook.ReleaseGrades.Released;
		
		// letter scales
		List<GradingScale> currentLetterScales = gb.getContextGradingScales();
		Map<String, Float> transferLetterGrades = new HashMap<String, Float>();
		for (GradingScale gs: currentLetterScales)
		{
			String gsScale = gs.getScaleCode();
			Integer gsSize = (Integer)artifact.getProperties().get("gradingScalePercentCount_"+gsScale);
		
			for (int count=1; count <= gsSize; count++)
			{
				String letterGrade = (String) artifact.getProperties().get("gradingScalePercent_" + gsScale + "_"+ count + "_letter");
				Float letterGradePercent = (Float) artifact.getProperties().get("gradingScalePercent_" + gsScale + "_"+ count + "_letterP");
				if (letterGrade != null && letterGradePercent != null) transferLetterGrades.put(letterGrade, letterGradePercent);
			}
		
			if (gsScale.equals(defaultScale)) gradebookService.modifyContextGradebook(siteId, showLetter, releaseGrade, gs.getId(), transferLetterGrades, modifiedByUserId);
			else gradebookService.modifyGradingScale(gb.getId(), gs.getId(), transferLetterGrades, modifiedByUserId);
		}			
		
		//Categories
		Integer categoryTypeCode = (Integer) artifact.getProperties().get("categoriesType");
		Gradebook.CategoryType categoryType = (new Integer(2).equals(categoryTypeCode)) ? Gradebook.CategoryType.Custom : Gradebook.CategoryType.Standard;
		gradebookService.modifyContextGradebookCategoryType(siteId, categoryType, modifiedByUserId);
		
		Integer categoryCount = (Integer)artifact.getProperties().get("categoryCount");
		if (categoryCount != null)
		{
			List<GradebookCategory> toGradebookCategories = gradebookImportService.findE3ContextCategories(gb.getId());
			List<GradebookCategory> transferCategories = new ArrayList<GradebookCategory>();
			for (int count=1; count <= categoryCount; count++)
			{
				String title = (String) artifact.getProperties().get("category_" + count + "_title");
				Float weight = (Float) artifact.getProperties().get("category_" + count + "_weight");
				Integer distribution = (Integer) artifact.getProperties().get("category_" + count + "_weightDistribution");
				WeightDistribution weightDistribution = (new Integer(1).equals(distribution)) ? GradebookCategory.WeightDistribution.Equally : GradebookCategory.WeightDistribution.Points;
				Integer lowestDrop = (Integer) artifact.getProperties().get("category_" + count + "_lowestDrop");
				Integer type = (Integer) artifact.getProperties().get("category_" + count + "_type");
				Gradebook.CategoryType gcCategoryType = (new Integer(2).equals(type)) ? Gradebook.CategoryType.Custom : Gradebook.CategoryType.Standard;
				Integer categoryCode = (Integer) artifact.getProperties().get("category_" + count + "_code");
				Integer categoryOrder = (Integer) artifact.getProperties().get("category_" + count + "_order");
			
				gradebookImportService.addCategoryforTransfer(toGradebookCategories,transferCategories, gcCategoryType, categoryCode, title, weight, weightDistribution, lowestDrop, categoryOrder);	
			}
			//retain toSite additional categories
			toGradebookCategories.removeAll(transferCategories);
			if (toGradebookCategories != null && toGradebookCategories.size() > 0)
			{
				transferCategories.addAll(toGradebookCategories);
			}
			
			//separate them to 2 arrays as api don't support sending as one
			List<GradebookCategory> standardCategories = new ArrayList<GradebookCategory>();
			List<GradebookCategory> customCategories = new ArrayList<GradebookCategory>();
			
			for (GradebookCategory gc : transferCategories)
			{
				if (gc.getCategoryType() == Gradebook.CategoryType.Standard) standardCategories.add(gc);
				else if (gc.getCategoryType() == Gradebook.CategoryType.Custom) customCategories.add(gc);
			}
			
			if (standardCategories.size() > 0)
				gradebookService.addModifyDeleteContextGradebookCategories(siteId, Gradebook.CategoryType.Standard, standardCategories, modifiedByUserId);
			
			if (customCategories.size() > 0)
				gradebookService.addModifyDeleteContextGradebookCategories(siteId, Gradebook.CategoryType.Custom, customCategories, modifiedByUserId);
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
	 * Set the gradebook import service.
	 * 
	 * @param service
	 *        The gradebook import service.
	 */
	public void setGradebookImportService(GradebookImportService gradebookImportService)
	{
		this.gradebookImportService = gradebookImportService;
	}
	
	/**
	 * {@inheritDoc}
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
	 * Do a write query.
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void write(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				writeTx(query, fields);
			}
		}, "write: " + fields[0] + " " + query);
	}

	/**
	 * Do a write query (transaction code)
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void writeTx(String query, Object[] fields)
	{
		if (!this.sqlService.dbWrite(query.toLowerCase(), fields))
		{
			throw new RuntimeException("writeTx: db write failed: " + fields[0] + " " + query);
		}
	}
}
