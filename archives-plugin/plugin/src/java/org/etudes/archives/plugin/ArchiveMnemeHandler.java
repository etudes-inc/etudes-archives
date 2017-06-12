/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveMnemeHandler.java $
 * $Id: ArchiveMnemeHandler.java 10998 2015-06-02 22:32:43Z mallikamt $
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.Attachment;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.mneme.api.Attribution;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.PartDetail;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolDraw;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.mneme.api.QuestionService;
import org.etudes.util.XrefHelper;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

/**
 * Archives archive handler for Mneme
 */
public class ArchiveMnemeHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveMnemeHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.mneme";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AssessmentService. */
	protected AssessmentService assessmentService = null;

	/** Dependency: AttachmentService */
	protected AttachmentService attachmentService = null;

	/** Dependency: PoolService. */
	protected PoolService poolService = null;

	/** Dependency: QuestionService. */
	protected QuestionService questionService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// docs - all of them, with thumbs, even if not referenced
		List<Attachment> allDocs = this.attachmentService.findFiles(AttachmentService.MNEME_APPLICATION, siteId, AttachmentService.DOCS_AREA);
		allDocs.addAll(this.attachmentService.findThumbs(AttachmentService.MNEME_APPLICATION, siteId, AttachmentService.DOCS_AREA));
		if (!allDocs.isEmpty())
		{
			Artifact artifact = archive.newArtifact(applicationId, "/mneme/docs");

			// cover all documents in Mneme Docs, even if not referenced
			Set<String> refs = new HashSet<String>();
			for (Attachment attachment : allDocs)
			{
				refs.add(attachment.getReference());
			}

			artifact.getReferences().addAll(refs);
			archive.archive(artifact);
		}

		// pools
		List<Pool> pools = this.poolService.getPools(siteId);
		for (Pool pool : pools)
		{
			// pool
			Artifact artifact = archive.newArtifact(applicationId, "/mneme/pool/" + pool.getId());
			artifact.getProperties().put("id", pool.getId());
			artifact.getProperties().put("title", pool.getTitle());
			artifact.getProperties().put("description", pool.getDescription());
			artifact.getProperties().put("difficulty", pool.getDifficulty());
			if (pool.getPointsEdit() != null) artifact.getProperties().put("points", pool.getPoints());
			addAttribution(artifact, "created", pool.getCreatedBy());
			addAttribution(artifact, "modified", pool.getModifiedBy());

			// description might have references
			if (pool.getDescription() != null)
			{
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(pool.getDescription(), null);
				artifact.getReferences().addAll(refs);
			}

			archive.archive(artifact);

			// questions (survey and not, only valid)
			List<String> questionIds = this.questionService.getPoolQuestionIds(pool, null, true);
			for (String questionId : questionIds)
			{
				Question question = this.questionService.getQuestion(questionId);

				artifact = archive.newArtifact(applicationId, "/mneme/question/" + question.getId());
				artifact.getProperties().put("id", question.getId());
				artifact.getProperties().put("poolId", question.getPool().getId());
				artifact.getProperties().put("pool", question.getPool().getTitle());
				addAttribution(artifact, "created", question.getCreatedBy());
				addAttribution(artifact, "modified", question.getModifiedBy());
				artifact.getProperties().put("title", question.getTitle());
				artifact.getProperties().put("description", question.getDescription());
				artifact.getProperties().put("reason", question.getExplainReason());
				artifact.getProperties().put("feedback", question.getFeedback());
				artifact.getProperties().put("hint", question.getHints());
				artifact.getProperties().put("survey", question.getIsSurvey());
				artifact.getProperties().put("points", question.getPoints());
				artifact.getProperties().put("presentationText", question.getPresentation().getText());
				artifact.getProperties().put("type", question.getType());
				artifact.getProperties().put("data", question.getTypeSpecificQuestion().getData());

				// harvest all references
				Set<String> refs = new HashSet<String>();
				if (question.getFeedback() != null)
				{
					refs = XrefHelper.harvestEmbeddedReferences(question.getFeedback(), null);
				}
				if (question.getHints() != null)
				{
					refs.addAll(XrefHelper.harvestEmbeddedReferences(question.getHints(), null));
				}
				if (question.getPresentation().getText() != null)
				{
					refs.addAll(XrefHelper.harvestEmbeddedReferences(question.getPresentation().getText(), null));
				}
				for (String s : question.getTypeSpecificQuestion().getData())
				{
					if (s != null)
					{
						refs.addAll(XrefHelper.harvestEmbeddedReferences(s, null));
					}
				}

				// add attachments
				List<Reference> attachments = question.getPresentation().getAttachments();
				List<String> attachmentReferences = new ArrayList<String>();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
					attachmentReferences.add(attachment.getReference());
				}
				artifact.getProperties().put("presentationAttachments", attachmentReferences);

				artifact.getReferences().addAll(refs);

				archive.archive(artifact);
			}
		}

		// assessments
		// each assessment is an artifact
		// parts are a collection
		// each part is a map
		// details are a collection in the part map
		// each detail is a map in that collection
		List<Assessment> assessments = this.assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		for (Assessment assessment : assessments)
		{
			// harvest all references (Note: only content ones, not pool or question ones)
			Set<String> refs = new HashSet<String>();

			Artifact artifact = archive.newArtifact(applicationId, "/mneme/assessment/" + assessment.getId());
			artifact.getProperties().put("id", assessment.getId());
			artifact.getProperties().put("valid", assessment.getIsValid());
			addAttribution(artifact, "created", assessment.getCreatedBy());
			addAttribution(artifact, "modified", assessment.getModifiedBy());
			if (assessment.getDates().getAcceptUntilDate() != null)
				artifact.getProperties().put("dateAcceptUntil", Long.valueOf(assessment.getDates().getAcceptUntilDate().getTime()));
			if (assessment.getDates().getDueDate() != null)
				artifact.getProperties().put("dateDue", Long.valueOf(assessment.getDates().getDueDate().getTime()));
			if (assessment.getDates().getOpenDate() != null)
				artifact.getProperties().put("dateOpen", Long.valueOf(assessment.getDates().getOpenDate().getTime()));
			artifact.getProperties().put("hideUntilOpen", assessment.getDates().getHideUntilOpen());
			artifact.getProperties().put("gradingAnonymous", assessment.getGrading().getAnonymous());
			artifact.getProperties().put("gradingAutoRelease", assessment.getGrading().getAutoRelease());
			artifact.getProperties().put("gradingGradebookIntegration", assessment.getGrading().getGradebookIntegration());

			artifact.getProperties().put("shuffleChoicesOverride", assessment.getShuffleChoicesOverride());

			artifact.getProperties().put("partsContinuous", assessment.getParts().getContinuousNumbering());
			artifact.getProperties().put("partsCount", assessment.getParts().getNumParts());
			artifact.getProperties().put("partsShowPresentation", assessment.getParts().getShowPresentation());

			if (assessment.getPoolId() != null) artifact.getProperties().put("poolId", assessment.getPoolId());

			// a collection for the artifact for the parts
			List<Map<String, Object>> partsCollection = new ArrayList<Map<String, Object>>();
			artifact.getProperties().put("parts", partsCollection);

			List<Part> parts = assessment.getParts().getParts();
			for (Part part : parts)
			{
				// a map for the part
				Map<String, Object> partMap = new HashMap<String, Object>();
				partsCollection.add(partMap);

				partMap.put("presentationText", part.getPresentation().getText());

				// add presentation attachments
				List<Reference> attachments = part.getPresentation().getAttachments();
				List<String> attachmentReferences = new ArrayList<String>();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
					attachmentReferences.add(attachment.getReference());
				}
				partMap.put("presentationAttachments", attachmentReferences);

				refs.addAll(XrefHelper.harvestEmbeddedReferences(part.getPresentation().getText(), null));
				partMap.put("title", part.getTitle());
				partMap.put("order", part.getOrdering().getPosition());
				partMap.put("randomize", part.getRandomize());

				// a collection for the artifact for the details
				List<Map<String, Object>> detialsCollection = new ArrayList<Map<String, Object>>();
				partMap.put("details", detialsCollection);

				for (PartDetail detail : part.getDetails())
				{
					// set the detail to the original, non-historical pools and questions
					if (detail.restoreToOriginal(null, null))
					{
						Map<String, Object> detailMap = new HashMap<String, Object>();
						detialsCollection.add(detailMap);
						detailMap.put("id", detail.getId());
						detailMap.put("order", detail.getOrdering().getPosition());
						if (detail.getPoints() != null) detailMap.put("points", detail.getPoints());

						if (detail instanceof PoolDraw)
						{
							PoolDraw pd = (PoolDraw) detail;
							detailMap.put("numQuestions", pd.getNumQuestions());
							detailMap.put("poolId", pd.getPoolId());
						}
						else if (detail instanceof QuestionPick)
						{
							QuestionPick qp = (QuestionPick) detail;
							detailMap.put("questionId", qp.getQuestionId());
						}
					}
				}
			}

			artifact.getProperties().put("password", assessment.getPassword().getPassword());

			if (assessment.getType() == AssessmentType.offline) artifact.getProperties().put("points", assessment.getPoints());

			artifact.getProperties().put("presentationText", assessment.getPresentation().getText());

			artifact.getProperties().put("published", assessment.getPublished());
			artifact.getProperties().put("needsPoints", assessment.getNeedsPoints());
			artifact.getProperties().put("questionGrouping", assessment.getQuestionGrouping().toString());
			artifact.getProperties().put("random", assessment.getRandomAccess());
			artifact.getProperties().put("honor", assessment.getRequireHonorPledge());
			if (assessment.getReview().getDate() != null) artifact.getProperties().put("reviewDate", assessment.getReview().getDate().getTime());
			artifact.getProperties().put("reviewCorrect", assessment.getReview().getShowCorrectAnswer().toString());
			artifact.getProperties().put("reviewFeedback", assessment.getReview().getShowFeedback());
			artifact.getProperties().put("reviewTiming", assessment.getReview().getTiming().toString());
			artifact.getProperties().put("reviewShowSummary", assessment.getReview().getShowSummary());
			artifact.getProperties().put("minScoreSet", assessment.getMinScoreSet());
			artifact.getProperties().put("minScore", assessment.getMinScore());

			artifact.getProperties().put("hints", assessment.getShowHints());
			artifact.getProperties().put("modelAnswer", assessment.getShowModelAnswer());
			artifact.getProperties().put("submitPresentationText", assessment.getSubmitPresentation().getText());
			if (assessment.getTimeLimit() != null) artifact.getProperties().put("timeLimit", assessment.getTimeLimit());
			artifact.getProperties().put("title", assessment.getTitle());
			if (assessment.getTries() != null) artifact.getProperties().put("tries", assessment.getTries());
			artifact.getProperties().put("type", assessment.getType().toString());
			if (assessment.getResultsEmail() != null) artifact.getProperties().put("resultsEmail", assessment.getResultsEmail());
			if (assessment.getResultsSent() != null) artifact.getProperties().put("resultsSent", assessment.getResultsSent().getTime());
			artifact.getProperties().put("formalCourseEval", assessment.getFormalCourseEval());
			artifact.getProperties().put("notifyEval", assessment.getNotifyEval());
			if (assessment.getEvaluationSent() != null) artifact.getProperties().put("evaluationSent", assessment.getEvaluationSent().getTime());

			refs.addAll(XrefHelper.harvestEmbeddedReferences(assessment.getPresentation().getText(), null));
			refs.addAll(XrefHelper.harvestEmbeddedReferences(assessment.getSubmitPresentation().getText(), null));

			// add presentation attachments
			List<Reference> attachments = assessment.getPresentation().getAttachments();
			List<String> attachmentReferences = new ArrayList<String>();
			for (Reference attachment : attachments)
			{
				refs.add(attachment.getReference());
				attachmentReferences.add(attachment.getReference());
			}
			artifact.getProperties().put("presentationAttachments", attachmentReferences);

			// add submit presentation attachments
			attachments = assessment.getSubmitPresentation().getAttachments();
			attachmentReferences = new ArrayList<String>();
			for (Reference attachment : attachments)
			{
				refs.add(attachment.getReference());
				attachmentReferences.add(attachment.getReference());
			}
			artifact.getProperties().put("submitPresentationAttachments", attachmentReferences);

			artifact.getReferences().addAll(refs);

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
	 * Dependency: AttachmentService.
	 * 
	 * @param service
	 *        The AttachmentService.
	 */
	public void setAttachmentService(AttachmentService service)
	{
		attachmentService = service;
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
	 * Set the UserDirectoryService.
	 * 
	 * @param service
	 *        The UserDirectoryService.
	 */
	public void setUserDirectoryService(UserDirectoryService service)
	{
		this.userDirectoryService = service;
	}

	/**
	 * Store the attribution in the artifact.
	 * 
	 * @param artifact
	 *        The artifact.
	 * @param key
	 *        The root of the keys.
	 * @param attribution
	 *        The attribution.
	 */
	protected void addAttribution(Artifact artifact, String key, Attribution attribution)
	{
		if (attribution.getDate() != null) artifact.getProperties().put(key + "Date", attribution.getDate().getTime());
		artifact.getProperties().put(key + "UserId", attribution.getUserId());
		try
		{
			artifact.getProperties().put(key + "User", this.userDirectoryService.getUser(attribution.getUserId()).getDisplayName());
		}
		catch (UserNotDefinedException e)
		{
		}
	}
}
