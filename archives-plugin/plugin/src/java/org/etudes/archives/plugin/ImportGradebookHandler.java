/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportGradebookHandler.java $
 * $Id: ImportGradebookHandler.java 11336 2015-07-21 18:30:00Z murthyt $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2013, 2014 Etudes, Inc.
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.gradebook.api.GradingScale;
import org.etudes.gradebook.api.Gradebook.ReleaseGrades;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentPolicyException;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.AssessmentType;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.service.gradebook.shared.GradebookService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives import handler for Gradebook
 */
public class ImportGradebookHandler implements ImportHandler
{
	class GradebookOptions
	{
		Boolean assignmentsDisplayed;
		Boolean courseGradeDisplayed;
		Boolean toDateGradeDisplayed;
		Boolean toDatePointsDisplayed;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportGradebookHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.gradebook.tool";
	protected final static String etudesApplicationId = "e3.gradebook";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AssessmentService. */
	protected AssessmentService assessmentService = null;

	/** Dependency: Etudes GradebookService. */
	protected org.etudes.gradebook.api.GradebookService etudesGradebookService = null;

	/** Dependency: GradebookService. */
	protected GradebookService gradebookService = null;

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
	 * {@inheritDoc}
	 */
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId) || !toolIds.contains(etudesApplicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		boolean toContextGB = gradebookService.isGradebookDefined(siteId);
		if (toContextGB)
		{
			// for a gradebook entry
			if (artifact.getReference().equals("/options"))
			{
				GradebookOptions options = new GradebookOptions();
				options.assignmentsDisplayed = ((Boolean) artifact.getProperties().get("assignmentsDisplayed")).booleanValue();
				options.courseGradeDisplayed = ((Boolean) artifact.getProperties().get("courseGradeDisplayed")).booleanValue();
				options.toDateGradeDisplayed = ((Boolean) artifact.getProperties().get("toDateGradeDisplayed")).booleanValue();
				options.toDatePointsDisplayed = ((Boolean) artifact.getProperties().get("toDatePointsDisplayed")).booleanValue();
	
				if (gradebookService.isGradebookDefined(siteId))
				{
					// update the gradebook
					Object[] fields = new Object[5];
					fields[0] = options.assignmentsDisplayed ? Integer.valueOf(1) : Integer.valueOf(0);
					fields[1] = options.courseGradeDisplayed ? Integer.valueOf(1) : Integer.valueOf(0);
					fields[2] = options.toDateGradeDisplayed ? Integer.valueOf(1) : Integer.valueOf(0);
					fields[3] = options.toDatePointsDisplayed ? Integer.valueOf(1) : Integer.valueOf(0);
					fields[4] = siteId;
					write("UPDATE GB_GRADEBOOK_T SET ASSIGNMENTS_DISPLAYED = ?, COURSE_GRADE_DISPLAYED = ?, TODATE_GRADE_DISPLAYED = ?, TODATE_POINTS_DISPLAYED = ? WHERE GRADEBOOK_UID = ?",
							fields);
				}
				else
				{
					M_log.warn("importArtifact: options: no gradebook for site: " + siteId);
				}
				
							
			}
		}
		else if (etudesGradebookService.isToolAvailable(siteId))
		{
			// import options to etudes gradebook. Note : Should this code be in ImportE3GradebookHandler??? If UCB gradebook is removed from tools deployment this code may not run.
			if (artifact.getReference().equals("/options"))
			{
				GradebookOptions options = new GradebookOptions();
				options.assignmentsDisplayed = ((Boolean) artifact.getProperties().get("assignmentsDisplayed")).booleanValue();
				options.courseGradeDisplayed = ((Boolean) artifact.getProperties().get("courseGradeDisplayed")).booleanValue();
				options.toDateGradeDisplayed = ((Boolean) artifact.getProperties().get("toDateGradeDisplayed")).booleanValue();
				options.toDatePointsDisplayed = ((Boolean) artifact.getProperties().get("toDatePointsDisplayed")).booleanValue();
				
				ReleaseGrades releaseGrades = ReleaseGrades.Released;;
				
				if (options.toDateGradeDisplayed)
				{
					releaseGrades = ReleaseGrades.Released;
				}
				etudesGradebookService.modifyContextGradebookAttributes(siteId, options.courseGradeDisplayed, releaseGrades, null, UserDirectoryService.getCurrentUser().getId());
			}			
			else if (artifact.getReference().equals("/selectedGradingScale"))
			{
				// TODO import grading scale if available
				String selectedGradingScaleUid = null;
				String selectedGradingScaleName = null;
				int selectedGradingScaleTypeId = 0;
				int selectedGradingScaleMapId = 0;
				
				if (etudesGradebookService.isToolAvailable(siteId))
				{
					if (artifact.getProperties().get("selectedGradingScaleUid") != null)
					{
						selectedGradingScaleUid = ((String) artifact.getProperties().get("selectedGradingScaleUid"));
					}
					
					if (artifact.getProperties().get("selectedGradingScaleName") != null)
					{
						selectedGradingScaleName = ((String) artifact.getProperties().get("selectedGradingScaleName"));
					}
					
					if (artifact.getProperties().get("selectedGradingScaleTypeId") != null)
					{
						selectedGradingScaleTypeId = ((Integer) artifact.getProperties().get("selectedGradingScaleTypeId"));
					}
					
					if (artifact.getProperties().get("selectedGradingScaleMapId") !=null)
					{
						selectedGradingScaleMapId = ((Integer) artifact.getProperties().get("selectedGradingScaleMapId"));
					}
					
					//Set keys = artifact.getProperties().keySet();
					
					Map<String, Float> letterGradeValues = new HashMap<String, Float>();
					
					for (Map.Entry<String, Object> entry : artifact.getProperties().entrySet()) 
					{
						M_log.debug("Key : " + entry.getKey() + " Value : " + entry.getValue());
						
						if (entry.getKey().startsWith("selectedGradingScaleLetterGradesPercent_"))
						{
							String letterGrade = entry.getKey().substring("selectedGradingScaleLetterGradesPercent_".length());
							
							if (entry.getValue() != null)
							{
								Float percent = (Float)entry.getValue();
								letterGradeValues.put(letterGrade, percent);
							}
						}
					}
					
					if (selectedGradingScaleTypeId > 0 && selectedGradingScaleUid != null)
					{
						org.etudes.gradebook.api.Gradebook gradebook = etudesGradebookService.getContextGradebook(siteId, UserDirectoryService.getCurrentUser().getId());
						
						List<GradingScale> gradingScales = gradebook.getContextGradingScales();
						
						for (GradingScale gradingScale : gradingScales)
						{
							// assuming both have same type id's
							if (selectedGradingScaleTypeId == gradingScale.getType().getScaleType())
							{
								etudesGradebookService.modifyContextGradebook(siteId, gradebook.isShowLetterGrade(), gradebook.getReleaseGrades(), gradingScale.getId(), letterGradeValues, UserDirectoryService.getCurrentUser().getId());
								break;
							}
						}
					}
				}			
			}
		}
		
		if (artifact.getReference().startsWith("/gradebook/"))
		{
			String title = (String) artifact.getProperties().get("name");

			// check for existing name conflict
			List<Assessment> assessments = this.assessmentService.getContextAssessments(siteId, AssessmentService.AssessmentsSort.cdate_a,
					Boolean.FALSE);
			Assessment found = null;
			for (Assessment candidate : assessments)
			{
				if (!StringUtil.different(candidate.getTitle(), title))
				{
					// return without saving the new assessment - it will stay mint and be cleared
					found = candidate;
					break;
				}
			}

			if (found == null)
			{
				Long termId = this.siteService.getSiteTermId(siteId);

				// for terms before or in F14 (7..42), import into the gradebook
				if ((termId != null) && (termId >= 7) && (termId <= 42))
				{
					double points = (Double) artifact.getProperties().get("points");
					Date dueDate = null;
					if (artifact.getProperties().get("due") != null) dueDate = new Date((Long) artifact.getProperties().get("due"));

					boolean isNotCounted = ((Boolean) artifact.getProperties().get("notCounted")).booleanValue();
					boolean isReleased = ((Boolean) artifact.getProperties().get("released")).booleanValue();

					// merge into the gradebook
					if (toContextGB && !gradebookService.isAssignmentDefined(siteId, title))
					{
						try
						{
							// create the assignment
							gradebookService.createAssignment(siteId, title, points, dueDate, isNotCounted, isReleased);
						}
						catch (Exception e)
						{
							M_log.warn("importArtifact: " + e);
						}
					}
				}

				// else import into AT&S as an offline
				else
				{
					Float points = Float.valueOf(((Double) artifact.getProperties().get("points")).floatValue());
					Date dueDate = null;
					if (artifact.getProperties().get("due") != null) dueDate = new Date((Long) artifact.getProperties().get("due"));

					try
					{
						Assessment assessment = this.assessmentService.newAssessment(siteId);
						assessment.setType(AssessmentType.offline);
						assessment.setTitle(title);
						assessment.setPoints(points);
						if (dueDate != null) assessment.getDates().setDueDate(dueDate);

						this.assessmentService.saveAssessment(assessment);
					}
					catch (AssessmentPermissionException e)
					{
						M_log.warn("importAssessment: " + e.toString());
					}
					catch (AssessmentPolicyException e)
					{
						M_log.warn("importAssessment: " + e.toString());
					}
				}
			}
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
	 * Set the AssessmentService.
	 * 
	 * @param service
	 *        The AssessmentService.
	 */
	public void setAssessmentService(AssessmentService service)
	{
		this.assessmentService = service;
	}

	/**
	 * @param etudesGradebookService the etudesGradebookService to set
	 */
	public void setEtudesGradebookService(org.etudes.gradebook.api.GradebookService etudesGradebookService)
	{
		this.etudesGradebookService = etudesGradebookService;
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
