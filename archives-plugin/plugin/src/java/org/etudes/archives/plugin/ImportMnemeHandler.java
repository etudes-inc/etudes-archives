/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportMnemeHandler.java $
 * $Id: ImportMnemeHandler.java 10998 2015-06-02 22:32:43Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, 2014, 2015 Etudes, Inc.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentPolicyException;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolDraw;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionGrouping;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.mneme.api.QuestionPoolService.FindQuestionsSort;
import org.etudes.mneme.api.QuestionService;
import org.etudes.mneme.api.ReviewShowCorrect;
import org.etudes.mneme.api.ReviewTiming;
import org.etudes.util.TranslationImpl;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.util.StringUtil;

/**
 * Archives import handler for Mneme
 */
public class ImportMnemeHandler implements ImportHandler
{
	/** The application Id. */
	protected final static String applicationId = "sakai.mneme";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportMnemeHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AssessmentService. */
	protected AssessmentService assessmentService = null;

	/** Dependency: EntityManager */
	protected EntityManager entityManager = null;

	/** Dependency: PoolService. */
	protected PoolService poolService = null;

	/** Dependency: QuestionService. */
	protected QuestionService questionService = null;

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
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		if (artifact.getReference().startsWith("/mneme/pool"))
		{
			importPool(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/mneme/question"))
		{
			importQuestion(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/mneme/assessment"))
		{
			importAssessment(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/mneme/docs"))
		{
			// nothing to process here - the references to CHS include the mneme docs in the archive
		}
		else
		{
			M_log.warn("importArtifact: unknown type: " + artifact.getReference());
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
	 * Dependency: EntityManager.
	 * 
	 * @param service
	 *        The EntityManager.
	 */
	public void setEntityManager(EntityManager service)
	{
		this.entityManager = service;
	}

	/**
	 * Set the PoolService.
	 * 
	 * @param service
	 *        The PoolService.
	 */
	public void setPoolService(PoolService service)
	{
		this.poolService = service;
	}

	/**
	 * Set the QuestionService.
	 * 
	 * @param service
	 *        The QuestionService.
	 */
	public void setQuestionService(QuestionService service)
	{
		this.questionService = service;
	}

	/**
	 * Import an assessment.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	@SuppressWarnings("unchecked")
	protected void importAssessment(String siteId, Artifact artifact, Archive archive)
	{
		try
		{
			Assessment assessment = this.assessmentService.newAssessment(siteId);

			// the auto-pool - link to one imported
			if ((String) artifact.getProperties().get("poolId") != null)
			{
				String poolRef = "/mneme/pool/" + (String) artifact.getProperties().get("poolId");
				String poolId = null;
				for (Translation t : archive.getTranslations())
				{
					if (t.getFrom().equals(poolRef))
					{
						poolId = t.getTo().substring("/mneme/pool/".length());
						break;
					}
				}
				if (poolId != null)
				{
					Pool pool = this.poolService.getPool(poolId);
					if (pool == null)
					{
						M_log.warn("importAssessment: missing auto pool: poolId: " + poolId);
					}
					else
					{
						assessment.setPool(pool);
					}
				}
			}

			if (artifact.getProperties().get("shuffleChoicesOverride") != null)
				assessment.setShuffleChoicesOverride(((Boolean) artifact.getProperties().get("shuffleChoicesOverride")));

			assessment.getGrading().setAnonymous((Boolean) artifact.getProperties().get("gradingAnonymous"));
			assessment.getGrading().setAutoRelease((Boolean) artifact.getProperties().get("gradingAutoRelease"));
			assessment.getGrading().setGradebookIntegration((Boolean) artifact.getProperties().get("gradingGradebookIntegration"));

			if (artifact.getProperties().get("dateAcceptUntil") != null)
				assessment.getDates().setAcceptUntilDate(new Date((Long) artifact.getProperties().get("dateAcceptUntil")));
			if (artifact.getProperties().get("dateDue") != null)
				assessment.getDates().setDueDate(new Date((Long) artifact.getProperties().get("dateDue")));
			if (artifact.getProperties().get("dateOpen") != null)
				assessment.getDates().setOpenDate(new Date((Long) artifact.getProperties().get("dateOpen")));
			if (artifact.getProperties().get("hideUntilOpen") != null)
				assessment.getDates().setHideUntilOpen((Boolean) artifact.getProperties().get("hideUntilOpen"));

			if (artifact.getProperties().get("points") != null) assessment.setPoints((Float) artifact.getProperties().get("points"));

			assessment.getPassword().setPassword((String) artifact.getProperties().get("password"));

			String text = (String) artifact.getProperties().get("presentationText");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			assessment.getPresentation().setText(text);

			List<String> attachments = (List<String>) artifact.getProperties().get("presentationAttachments");
			if (attachments != null)
			{
				for (String attachment : attachments)
				{
					// change to the imported site's attachment
					for (Translation t : archive.getTranslations())
					{
						attachment = t.translate(attachment);
					}
					Reference ref = this.entityManager.newReference(attachment);
					assessment.getPresentation().getAttachments().add(ref);
				}
			}

			// Note: all come in unpublished
			assessment.setPublished(Boolean.FALSE);

			assessment.setQuestionGrouping(QuestionGrouping.valueOf((String) artifact.getProperties().get("questionGrouping")));
			assessment.setRandomAccess((Boolean) artifact.getProperties().get("random"));
			assessment.setRequireHonorPledge((Boolean) artifact.getProperties().get("honor"));

			if (artifact.getProperties().get("reviewDate") != null)
				assessment.getReview().setDate(new Date((Long) artifact.getProperties().get("reviewDate")));
			assessment.getReview().setShowCorrectAnswer(ReviewShowCorrect.valueOf((String) artifact.getProperties().get("reviewCorrect")));
			assessment.getReview().setShowFeedback((Boolean) artifact.getProperties().get("reviewFeedback"));
			assessment.getReview().setTiming(ReviewTiming.valueOf((String) artifact.getProperties().get("reviewTiming")));
			assessment.getReview().setShowSummary((Boolean) artifact.getProperties().get("reviewShowSummary"));
			assessment.setMinScoreSet((Boolean) artifact.getProperties().get("minScoreSet"));
			assessment.setMinScore((Integer) artifact.getProperties().get("minScore"));

			assessment.setShowHints((Boolean) artifact.getProperties().get("hints"));
			assessment.setShowModelAnswer((Boolean) artifact.getProperties().get("modelAnswer"));

			text = (String) artifact.getProperties().get("submitPresentationText");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			assessment.getSubmitPresentation().setText(text);

			attachments = (List<String>) artifact.getProperties().get("submitPresentationAttachments");
			if (attachments != null)
			{
				for (String attachment : attachments)
				{
					// change to the imported site's attachment
					for (Translation t : archive.getTranslations())
					{
						attachment = t.translate(attachment);
					}
					Reference ref = this.entityManager.newReference(attachment);
					assessment.getSubmitPresentation().getAttachments().add(ref);
				}
			}

			assessment.setTimeLimit((Long) artifact.getProperties().get("timeLimit"));
			assessment.setTitle((String) artifact.getProperties().get("title"));
			assessment.setTries((Integer) artifact.getProperties().get("tries"));
			assessment.initType(AssessmentType.valueOf((String) artifact.getProperties().get("type")));
			assessment.setNeedsPoints((Boolean) artifact.getProperties().get("needsPoints"));
			assessment.setResultsEmail((String) artifact.getProperties().get("resultsEmail"));
			assessment.setNotifyEval((Boolean) artifact.getProperties().get("notifyEval"));
			assessment.getParts().setContinuousNumbering((Boolean) artifact.getProperties().get("partsContinuous"));
			assessment.getParts().setShowPresentation((Boolean) artifact.getProperties().get("partsShowPresentation"));
			List<Map<String, Object>> partsCollection = (List<Map<String, Object>>) artifact.getProperties().get("parts");
			if (partsCollection != null)
			{
				for (Map<String, Object> partMap : partsCollection)
				{
					Part part = assessment.getParts().addPart();

					List<Map<String, Object>> detailsCollection = (List<Map<String, Object>>) partMap.get("details");
					if (detailsCollection != null)
					{
						for (Map<String, Object> detailMap : detailsCollection)
						{
							String qid = (String) detailMap.get("questionId");
							String pid = (String) detailMap.get("poolId");
							Integer numQuestions = (Integer) detailMap.get("numQuestions");
							Float points = (Float) detailMap.get("points");

							// for a pick
							if (qid != null)
							{
								// translate the question id to this site's questions
								String questionRef = "/mneme/question/" + qid;
								String questionId = null;
								for (Translation t : archive.getTranslations())
								{
									if (t.getFrom().equals(questionRef))
									{
										questionId = t.getTo().substring("/mneme/question/".length());
										break;
									}
								}

								if (questionId == null)
								{
									M_log.warn("importAssessment: missing question translation: questionId: " + qid);
									continue;
								}

								Question question = this.questionService.getQuestion(questionId);
								if (question == null)
								{
									M_log.warn("importAssessment: missing question: questionId: " + questionId + " for ref: " + questionRef);
									continue;
								}

								QuestionPick pick = part.addPickDetail(question);
								pick.setPoints(points);
							}

							// for a draw
							else if ((pid != null) && (numQuestions != null))
							{
								// find the pool - the draw's original pool should have a mapping in the archive
								String poolRef = "/mneme/pool/" + pid;
								String poolId = null;
								for (Translation t : archive.getTranslations())
								{
									if (t.getFrom().equals(poolRef))
									{
										poolId = t.getTo().substring("/mneme/pool/".length());
										break;
									}
								}
								if (poolId == null)
								{
									M_log.warn("importAssessment: missing draw pool translation: poolId: " + pid);
									continue;
								}

								Pool pool = this.poolService.getPool(poolId);
								if (pool == null)
								{
									M_log.warn("importAssessment: missing draw pool: poolId: " + poolId);
									continue;
								}

								PoolDraw draw = part.addDrawDetail(pool, numQuestions);
								draw.setPoints(points);
							}
						}
					}

					part.setTitle((String) partMap.get("title"));
					part.setRandomize((Boolean) partMap.get("randomize"));

					text = (String) partMap.get("presentationText");
					text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
					part.getPresentation().setText(text);

					attachments = (List<String>) partMap.get("presentationAttachments");
					if (attachments != null)
					{
						for (String attachment : attachments)
						{
							// change to the imported site's attachment
							for (Translation t : archive.getTranslations())
							{
								attachment = t.translate(attachment);
							}
							Reference ref = this.entityManager.newReference(attachment);
							part.getPresentation().getAttachments().add(ref);
						}
					}
				}
			}

			// check if we have an assessment that matches (check title only)
			List<Assessment> assessments = this.assessmentService.getContextAssessments(siteId, AssessmentService.AssessmentsSort.cdate_a,
					Boolean.FALSE);
			for (Assessment candidate : assessments)
			{
				if (!StringUtil.different(candidate.getTitle(), assessment.getTitle()))
				{
					// return without saving the new assessment - it will stay mint and be cleared
					return;
				}
			}
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

	/**
	 * Import a pool.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	protected void importPool(String siteId, Artifact artifact, Archive archive)
	{
		// if we have a pool with the same title in the site, we will just use that and skip the import
		List<Pool> pools = this.poolService.getPools(siteId);
		String title = (String) artifact.getProperties().get("title");
		for (Pool pool : pools)
		{
			if (!StringUtil.different(title, pool.getTitle()))
			{
				// will map references to this pool.getId() , artifact.getProperties().get("id");
				archive.getTranslations()
						.add(new TranslationImpl("/mneme/pool/" + artifact.getProperties().get("id"), "/mneme/pool/" + pool.getId()));

				return;
			}
		}

		try
		{
			Pool pool = this.poolService.newPool(siteId);
			pool.setTitle((String) artifact.getProperties().get("title"));

			String text = (String) artifact.getProperties().get("description");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			pool.setDescription(text);

			pool.setDifficulty((Integer) artifact.getProperties().get("difficulty"));
			pool.setPointsEdit((Float) artifact.getProperties().get("points"));
			this.poolService.savePool(pool);

			// will map references to this pool.getId() , artifact.getProperties().get("id");
			archive.getTranslations().add(new TranslationImpl("/mneme/pool/" + artifact.getProperties().get("id"), "/mneme/pool/" + pool.getId()));
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("importPool: " + e.toString());
		}
	}

	/**
	 * Import a question.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	@SuppressWarnings("unchecked")
	protected void importQuestion(String siteId, Artifact artifact, Archive archive)
	{
		// find the pool - the question's original pool should have a mapping in the archive
		String poolRef = "/mneme/pool/" + (String) artifact.getProperties().get("poolId");
		String poolId = null;
		for (Translation t : archive.getTranslations())
		{
			if (t.getFrom().equals(poolRef))
			{
				poolId = t.getTo().substring("/mneme/pool/".length());
				break;
			}
		}
		if (poolId == null)
		{
			M_log.warn("importQuestion: missing pool translation: poolId: " + (String) artifact.getProperties().get("poolId"));
			return;
		}

		Pool pool = this.poolService.getPool(poolId);
		if (pool == null)
		{
			M_log.warn("importQuestion: missing pool: poolId: " + poolId);
			return;
		}

		// if there is a question in the pool that "matches" this one, use it and skip the import
		// match by type,
		try
		{
			Question question = this.questionService.newQuestion(pool, (String) artifact.getProperties().get("type"));
			question.setExplainReason((Boolean) artifact.getProperties().get("reason"));

			String text = (String) artifact.getProperties().get("feedback");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			question.setFeedback(text);

			text = (String) artifact.getProperties().get("hint");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			question.setHints(text);

			question.setIsSurvey((Boolean) artifact.getProperties().get("survey"));

			text = (String) artifact.getProperties().get("presentationText");
			text = XrefHelper.translateEmbeddedReferences(text, archive.getTranslations(), null, null);
			question.getPresentation().setText(text);
			
			text = (String) artifact.getProperties().get("title");
			question.setTitle(text);

			List<String> attachments = (List<String>) artifact.getProperties().get("presentationAttachments");
			if (attachments != null)
			{
				for (String attachment : attachments)
				{
					// change to the imported site's attachment
					for (Translation t : archive.getTranslations())
					{
						attachment = t.translate(attachment);
					}
					Reference ref = this.entityManager.newReference(attachment);
					question.getPresentation().getAttachments().add(ref);
				}
			}

			String[] data = (String[]) artifact.getProperties().get("data");
			if (data != null)
			{
				for (int i = 0; i < data.length; i++)
				{
					data[i] = XrefHelper.translateEmbeddedReferences(data[i], archive.getTranslations(), null, null);
				}
				question.getTypeSpecificQuestion().setData(data);
			}

			// make sure the question is marked as changed (probably not needed)
			question.setChanged();

			// if there is a question in the pool that "matches" this one, use it and skip the import
			List<Question> questions = this.questionService.findQuestions(pool, FindQuestionsSort.cdate_a, null, question.getType(), null, null,
					null, null);
			for (Question candidate : questions)
			{
				if (candidate.matches(question))
				{
					// will map references to this question.getId() , artifact.getProperties().get("id");
					archive.getTranslations().add(
							new TranslationImpl("/mneme/question/" + artifact.getProperties().get("id"), "/mneme/question/" + candidate.getId()));

					// return without saving the new question - it will stay mint and be cleared
					return;
				}
			}

			// save the new question
			this.questionService.saveQuestion(question);

			// will map references to this question.getId() , artifact.getProperties().get("id");
			archive.getTranslations().add(
					new TranslationImpl("/mneme/question/" + artifact.getProperties().get("id"), "/mneme/question/" + question.getId()));
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("importPool: " + e.toString());
		}
	}
}
