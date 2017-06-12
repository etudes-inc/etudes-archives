/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveGradebookHandler.java $
 * $Id: ArchiveGradebookHandler.java 11360 2015-07-23 21:25:33Z murthyt $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2013 Etudes, Inc.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.GradebookService;

/**
 * Archives archive handler for Gradebook
 */
public class ArchiveGradebookHandler implements ArchiveHandler
{
	class Gradebook
	{
		int id;
		int selectedGradeMappingId;
		String uid;		
	}
	
	class GradebookOptions
	{
		Boolean assignmentsDisplayed;
		Boolean courseGradeDisplayed;
		Boolean toDateGradeDisplayed;
		Boolean toDatePointsDisplayed;
	}
	
	class GradebookScaleLetterGradePercent
	{
		Integer gradeMapId;
		String letterGrade;
		Float percent;		
	}
	
	class GradebookSelectedScale
	{
		int gradingMapId;
		String gradingScaleName;
		int gradingScaleTypeId;
		String gradingScaleUid;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveGradebookHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.gradebook.tool";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: GradebookService. */
	protected GradebookService gradebookService = null;
	
	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		boolean fromContextGB = gradebookService.isGradebookDefined(siteId);
		if (!fromContextGB) return;

		List<Assignment> fromContextAssignments = gradebookService.getAssignments(siteId);

		for (Assignment assignment : fromContextAssignments)
		{
			// manually/directly added assignments to gradebook
			if ((assignment.getExternalAppName() == null) && (assignment.getExternalId() == null))
			{
				if ((assignment.getName() != null) && (assignment.getName().trim().length() != 0))
				{
					String name = assignment.getName().trim();
					double points = assignment.getPoints();
					Date dueDate = assignment.getDueDate();
					boolean isNotCounted = assignment.isNotCounted();
					boolean isReleased = assignment.isReleased();

					// make an artifact
					Artifact artifact = archive.newArtifact(applicationId, "/gradebook/" + name);

					// set the values
					artifact.getProperties().put("name", name);
					artifact.getProperties().put("points", Double.valueOf(points));
					if (dueDate != null) artifact.getProperties().put("due", Long.valueOf(dueDate.getTime()));
					artifact.getProperties().put("notCounted", Boolean.valueOf(isNotCounted));
					artifact.getProperties().put("released", Boolean.valueOf(isReleased));

					// archive it
					archive.archive(artifact);
				}
			}
		}

		// options
		Artifact optionsArtifact = archive.newArtifact(applicationId, "/options");
		GradebookOptions options = readGradebookOptions(siteId);
		optionsArtifact.getProperties().put("assignmentsDisplayed", options.assignmentsDisplayed);
		optionsArtifact.getProperties().put("courseGradeDisplayed", options.courseGradeDisplayed);
		optionsArtifact.getProperties().put("toDateGradeDisplayed", options.toDateGradeDisplayed);
		optionsArtifact.getProperties().put("toDatePointsDisplayed", options.toDatePointsDisplayed);
		archive.archive(optionsArtifact);
		
		// gradebook
		Gradebook gradebook = selectGradebook(siteId);
		
		// for selected grading scale
		if (gradebook != null && gradebook.id > 0 && gradebook.selectedGradeMappingId > 0)
		{
			GradebookSelectedScale  gradebookSelectedScale = selectGradebookSelectedScale(gradebook.id);
			
			if (gradebookSelectedScale != null && gradebookSelectedScale.gradingMapId > 0)
			{
				List<GradebookScaleLetterGradePercent> gradebookScaleLetterGradePercents = selectGradebookGradingScaleLetterGradePercent(gradebookSelectedScale.gradingMapId);
				
				// selected grading scale details
				Artifact selectedGradingscaleArtifact = archive.newArtifact(applicationId, "/selectedGradingScale");
				
				selectedGradingscaleArtifact.getProperties().put("selectedGradingScaleUid", gradebookSelectedScale.gradingScaleUid);
				selectedGradingscaleArtifact.getProperties().put("selectedGradingScaleName", gradebookSelectedScale.gradingScaleName);
				selectedGradingscaleArtifact.getProperties().put("selectedGradingScaleTypeId", gradebookSelectedScale.gradingScaleTypeId);
				selectedGradingscaleArtifact.getProperties().put("selectedGradingScaleMapId", gradebookSelectedScale.gradingMapId);
				
				// get grading scale letter grade values
				for (GradebookScaleLetterGradePercent gradebookScaleLetterGradePercent : gradebookScaleLetterGradePercents)
				{
					selectedGradingscaleArtifact.getProperties().put("selectedGradingScaleLetterGradesPercent_"+ gradebookScaleLetterGradePercent.letterGrade, gradebookScaleLetterGradePercent.percent);
				}
				
				archive.archive(selectedGradingscaleArtifact);
			}
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
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}
	
	/**
	 * Read the gradebook options for this site.
	 * 
	 * @param siteId
	 *        The site Id
	 * @return a GradebookOptions containing the options.
	 */
	protected GradebookOptions readGradebookOptions(String siteId)
	{
		String sql = "SELECT ASSIGNMENTS_DISPLAYED, COURSE_GRADE_DISPLAYED, TODATE_GRADE_DISPLAYED, TODATE_POINTS_DISPLAYED FROM GB_GRADEBOOK_T WHERE GRADEBOOK_UID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		final GradebookOptions rv = new GradebookOptions();

		this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					rv.assignmentsDisplayed = "1".equals(result.getString(1));
					rv.courseGradeDisplayed = "1".equals(result.getString(2));
					rv.toDateGradeDisplayed = "1".equals(result.getString(3));
					rv.toDatePointsDisplayed = "1".equals(result.getString(4));

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readGradebookOptions: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}
	
	/**
	 * Get gradebook from database
	 * 
	 * @param siteId	Site id
	 * 
	 * @return	Gradebook or null of not existing
	 */
	protected Gradebook selectGradebook(String siteId)
	{
		String sql = "SELECT ID, GRADEBOOK_UID, NAME, SELECTED_GRADE_MAPPING_ID FROM gb_gradebook_t WHERE GRADEBOOK_UID = ?";
		
		Object[] fields = new Object[1];
		int i = 0;
		fields[i++] = siteId;
		
		final List<Gradebook> gradebooks =  new ArrayList<Gradebook>();
		
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Gradebook gradebook = new Gradebook();
					
					gradebook.id = result.getInt("ID");
					gradebook.uid = result.getString("GRADEBOOK_UID");
					gradebook.selectedGradeMappingId = result.getInt("SELECTED_GRADE_MAPPING_ID");
					
					gradebooks.add(gradebook);
					
					return null;
				}
				catch (SQLException e)
				{
					if (M_log.isWarnEnabled())
					{
						M_log.warn("selectGradebook: " + e, e);
					}
					return null;
				}
			}
		});
		
		if (gradebooks.size() == 1)
		{
			Gradebook gradebook = gradebooks.get(0);
			
			return gradebook;
		}
		
		return null;
	}
	
	/**
	 * Gets default or selected grading scale
	 * 
	 * @param gradingMapId	Grading map id
	 * 
	 * @return	Default or selected grading scale
	 */
	protected List<GradebookScaleLetterGradePercent> selectGradebookGradingScaleLetterGradePercent(int gradingMapId)
	{
		String sql = "SELECT GRADE_MAP_ID, PERCENT, LETTER_GRADE FROM gb_grade_to_percent_mapping_t WHERE GRADE_MAP_ID = ?";
		
		Object[] fields = new Object[1];
		int i = 0;
		fields[i++] = gradingMapId;
		
		final List<GradebookScaleLetterGradePercent> gradebookScaleLetterGradePercents =  new ArrayList<GradebookScaleLetterGradePercent>();
		
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					GradebookScaleLetterGradePercent gradebookScaleLetterGradePercent = new GradebookScaleLetterGradePercent();
					
					gradebookScaleLetterGradePercent.gradeMapId = result.getInt("GRADE_MAP_ID");
					gradebookScaleLetterGradePercent.letterGrade = result.getString("LETTER_GRADE");
					gradebookScaleLetterGradePercent.percent = result.getFloat("PERCENT");
					
					gradebookScaleLetterGradePercents.add(gradebookScaleLetterGradePercent);
					
					return null;
				}
				catch (SQLException e)
				{
					if (M_log.isWarnEnabled())
					{
						M_log.warn("selectGradebookGradingScalePercent: " + e, e);
					}
					return null;
				}
			}
		});
		
		return gradebookScaleLetterGradePercents;
	}
	
	/**
	 * Gets the default or selected grading scale
	 * 
	 * @param gradebookId	Gradebook id
	 *
	 * @return	The default or selected grading scale
	 */
	protected GradebookSelectedScale selectGradebookSelectedScale(int gradebookId)
	{
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT gbgs.SCALE_UID AS grading_scale_uid, gbgs.NAME AS grading_scale_name,  gbgm.GB_GRADING_SCALE_T AS grading_scale_type_id, gbgm.ID AS grading_map_id ");
		sql.append("FROM gb_grading_scale_t gbgs, gb_grade_map_t gbgm, gb_gradebook_t gbg ");
		sql.append("WHERE gbgm.GB_GRADING_SCALE_T = gbgs.ID ");
		sql.append("AND gbgm.GRADEBOOK_ID = gbg.ID ");
		sql.append("AND gbg.SELECTED_GRADE_MAPPING_ID = gbgm.ID ");
		sql.append("AND gbg.ID = ?");
		
		Object[] fields = new Object[1];
		int i = 0;
		fields[i++] = gradebookId;
		
		final List<GradebookSelectedScale> GradebookSelectedScales =  new ArrayList<GradebookSelectedScale>();
		
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					GradebookSelectedScale gradebookSelectedScale = new GradebookSelectedScale();
					
					gradebookSelectedScale.gradingScaleUid = result.getString("grading_scale_uid");
					gradebookSelectedScale.gradingScaleName = result.getString("grading_scale_name");
					gradebookSelectedScale.gradingScaleTypeId = result.getInt("grading_scale_type_id");
					gradebookSelectedScale.gradingMapId = result.getInt("grading_map_id");
					
					GradebookSelectedScales.add(gradebookSelectedScale);
					
					return null;
				}
				catch (SQLException e)
				{
					if (M_log.isWarnEnabled())
					{
						M_log.warn("selectGradebookSelectedScale: " + e, e);
					}
					return null;
				}
			}
		});
		
		if (GradebookSelectedScales.size() == 1)
		{
			GradebookSelectedScale gradebookSelectedScale = GradebookSelectedScales.get(0);
			
			return gradebookSelectedScale;
		}
		
		return null;
	}
	
	
}
