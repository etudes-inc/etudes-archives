/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefMnemeHandler.java $
 * $Id: XrefMnemeHandler.java 2823 2012-04-03 20:57:39Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009 Etudes, Inc.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionService;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementMessageEdit;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives cross reference handler for Mneme
 */
public class XrefMnemeHandler implements XrefHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefMnemeHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.mneme";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AssessmentService. */
	protected AssessmentService assessmentService = null;

	/** Dependency: AttachmentService. */
	protected AttachmentService attachmentService = null;

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
		this.archivesService.unRegisterXrefHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerXrefHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public int removeXref(String siteId)
	{
		// ignore user sites
		if (siteId.startsWith("~")) return 0;

		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		int count = 0;

		// pool description (all pools, even historical)
		List<Pool> pools = this.poolService.getAllPools(siteId);
		for (Pool pool : pools)
		{
			try
			{
				String description = pool.getDescription();
				if (description != null)
				{
					Set<String> refs = XrefHelper.harvestEmbeddedReferences(description, siteId);
					count += refs.size();

					// import into the attachment area, deep.
					List<Translation> translations = this.attachmentService.importResources(AttachmentService.MNEME_APPLICATION, siteId,
							AttachmentService.DOCS_AREA, AttachmentService.NameConflictResolution.keepExisting, refs,
							AttachmentService.MNEME_THUMB_POLICY, AttachmentService.REFERENCE_ROOT);

					// translate and shorten
					String newDescription = XrefHelper.translateEmbeddedReferencesAndShorten(description, translations, siteId, null);

					// save (won't really save if there was no change)
					if (StringUtil.different(description, newDescription))
					{
						pool.setDescription(newDescription);
						this.poolService.savePool(pool);
					}
				}
			}
			catch (Throwable t)
			{
				M_log.warn("removeXref: " + t.toString());
			}

			// questions (survey and not, valid and not)
			List<String> questionIds = this.questionService.getPoolQuestionIds(pool, null, null);
			for (String questionId : questionIds)
			{
				try
				{
					Set<String> refs = new HashSet<String>();
					Question question = this.questionService.getQuestion(questionId);
					if (question.getFeedback() != null)
					{
						refs = XrefHelper.harvestEmbeddedReferences(question.getFeedback(), siteId);
					}
					if (question.getHints() != null)
					{
						refs.addAll(XrefHelper.harvestEmbeddedReferences(question.getHints(), siteId));
					}
					if (question.getPresentation().getText() != null)
					{
						refs.addAll(XrefHelper.harvestEmbeddedReferences(question.getPresentation().getText(), siteId));
					}
					for (String s : question.getTypeSpecificQuestion().getData())
					{
						if (s != null)
						{
							refs.addAll(XrefHelper.harvestEmbeddedReferences(s, siteId));
						}
					}
					List<Reference> attachments = question.getPresentation().getAttachments();
					for (Reference attachment : attachments)
					{
						refs.add(attachment.getReference());
					}

					count += refs.size();

					// import into the attachment area, deep.
					List<Translation> translations = this.attachmentService.importResources(AttachmentService.MNEME_APPLICATION, siteId,
							AttachmentService.DOCS_AREA, AttachmentService.NameConflictResolution.keepExisting, refs,
							AttachmentService.MNEME_THUMB_POLICY, AttachmentService.REFERENCE_ROOT);

					// translate and shorten
					if (question.getFeedback() != null)
					{
						question.setFeedback(XrefHelper.translateEmbeddedReferencesAndShorten(question.getFeedback(), translations, siteId, null));
					}
					if (question.getHints() != null)
					{
						question.setHints(XrefHelper.translateEmbeddedReferencesAndShorten(question.getHints(), translations, siteId, null));
					}
					if (question.getPresentation().getText() != null)
					{
						question.getPresentation().setText(
								XrefHelper.translateEmbeddedReferencesAndShorten(question.getPresentation().getText(), translations, siteId, null));
					}
					String[] data = question.getTypeSpecificQuestion().getData();
					for (int i = 0; i < data.length; i++)
					{
						if (data[i] != null)
						{
							String translated = XrefHelper.translateEmbeddedReferencesAndShorten(data[i], translations, siteId, null);
							if (StringUtil.different(data[i], translated))
							{
								data[i] = translated;
								question.setChanged();
							}
						}
					}
					question.getTypeSpecificQuestion().setData(data);

					// attachments
					// TODO: shorten?
					boolean changed = false;
					List<Reference> newAttachments = new ArrayList<Reference>();
					for (Reference attachment : attachments)
					{
						String attachmentRef = attachment.getReference();
						String attachmentRefTranslated = attachmentRef;
						for (Translation t : translations)
						{
							attachmentRefTranslated = t.translate(attachmentRefTranslated);
						}
						if (StringUtil.different(attachmentRef, attachmentRefTranslated))
						{
							changed = true;
							newAttachments.add(this.entityManager.newReference(attachmentRefTranslated));
						}
						else
						{
							newAttachments.add(attachment);
						}
					}
					if (changed)
					{
						// Note: the clear won't mark the question as changed, but should clear the attachments -ggolden
						attachments.clear();
						for (Reference attachment : newAttachments)
						{
							question.getPresentation().addAttachment(attachment);
						}
					}

					// save (even if historical)
					this.questionService.saveQuestion(question, Boolean.TRUE);
				}
				catch (Throwable t)
				{
					M_log.warn("removeXref: " + t.toString());
				}
			}
		}

		// assessments
		List<Assessment> assessments = this.assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		// archived, too
		assessments.addAll(this.assessmentService.getArchivedAssessments(siteId));
		for (Assessment assessment : assessments)
		{
			try
			{
				Set<String> refs = new HashSet<String>();

				List<Part> parts = assessment.getParts().getParts();
				for (Part part : parts)
				{
					List<Reference> attachments = part.getPresentation().getAttachments();
					for (Reference attachment : attachments)
					{
						refs.add(attachment.getReference());
					}
					refs.addAll(XrefHelper.harvestEmbeddedReferences(part.getPresentation().getText(), siteId));
				}

				refs.addAll(XrefHelper.harvestEmbeddedReferences(assessment.getPresentation().getText(), siteId));
				List<Reference> attachments = assessment.getPresentation().getAttachments();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
				}

				refs.addAll(XrefHelper.harvestEmbeddedReferences(assessment.getSubmitPresentation().getText(), siteId));
				attachments = assessment.getSubmitPresentation().getAttachments();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
				}

				count += refs.size();

				// import into the attachment area, deep.
				List<Translation> translations = this.attachmentService.importResources(AttachmentService.MNEME_APPLICATION, siteId,
						AttachmentService.DOCS_AREA, AttachmentService.NameConflictResolution.keepExisting, refs,
						AttachmentService.MNEME_THUMB_POLICY, AttachmentService.REFERENCE_ROOT);

				// translate
				// TODO: shorten?
				for (Part part : parts)
				{
					attachments = part.getPresentation().getAttachments();
					boolean changed = false;
					List<Reference> newAttachments = new ArrayList<Reference>();
					for (Reference attachment : attachments)
					{
						String attachmentRef = attachment.getReference();
						String attachmentRefTranslated = attachmentRef;
						for (Translation t : translations)
						{
							attachmentRefTranslated = t.translate(attachmentRefTranslated);
						}
						if (StringUtil.different(attachmentRef, attachmentRefTranslated))
						{
							changed = true;
							newAttachments.add(this.entityManager.newReference(attachmentRefTranslated));
						}
						else
						{
							newAttachments.add(attachment);
						}
					}
					if (changed)
					{
						// Note: the clear won't mark the question as changed, but should clear the attachments -ggolden
						attachments.clear();
						for (Reference attachment : newAttachments)
						{
							part.getPresentation().addAttachment(attachment);
						}
					}

					part.getPresentation().setText(
							XrefHelper.translateEmbeddedReferencesAndShorten(part.getPresentation().getText(), translations, siteId, null));
				}

				attachments = assessment.getPresentation().getAttachments();
				boolean changed = false;
				List<Reference> newAttachments = new ArrayList<Reference>();
				for (Reference attachment : attachments)
				{
					String attachmentRef = attachment.getReference();
					String attachmentRefTranslated = attachmentRef;
					for (Translation t : translations)
					{
						attachmentRefTranslated = t.translate(attachmentRefTranslated);
					}
					if (StringUtil.different(attachmentRef, attachmentRefTranslated))
					{
						changed = true;
						newAttachments.add(this.entityManager.newReference(attachmentRefTranslated));
					}
					else
					{
						newAttachments.add(attachment);
					}
				}
				if (changed)
				{
					// Note: the clear won't mark the question as changed, but should clear the attachments -ggolden
					attachments.clear();
					for (Reference attachment : newAttachments)
					{
						assessment.getPresentation().addAttachment(attachment);
					}
				}
				assessment.getPresentation().setText(
						XrefHelper.translateEmbeddedReferencesAndShorten(assessment.getPresentation().getText(), translations, siteId, null));

				attachments = assessment.getSubmitPresentation().getAttachments();
				changed = false;
				newAttachments = new ArrayList<Reference>();
				for (Reference attachment : attachments)
				{
					String attachmentRef = attachment.getReference();
					String attachmentRefTranslated = attachmentRef;
					for (Translation t : translations)
					{
						attachmentRefTranslated = t.translate(attachmentRefTranslated);
					}
					if (StringUtil.different(attachmentRef, attachmentRefTranslated))
					{
						changed = true;
						newAttachments.add(this.entityManager.newReference(attachmentRefTranslated));
					}
					else
					{
						newAttachments.add(attachment);
					}
				}
				if (changed)
				{
					// Note: the clear won't mark the question as changed, but should clear the attachments -ggolden
					attachments.clear();
					for (Reference attachment : newAttachments)
					{
						assessment.getSubmitPresentation().addAttachment(attachment);
					}
				}
				assessment.getSubmitPresentation().setText(
						XrefHelper.translateEmbeddedReferencesAndShorten(assessment.getSubmitPresentation().getText(), translations, siteId, null));

				// save
				this.assessmentService.saveAssessment(assessment);
			}
			catch (Throwable t)
			{
				M_log.warn("removeXref: " + t.toString());
			}
		}

		return count;
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
	 * Set the AttachmentService.
	 * 
	 * @param service
	 *        The AttachmentService.
	 */
	public void setAttachmentService(AttachmentService service)
	{
		this.attachmentService = service;
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
}
