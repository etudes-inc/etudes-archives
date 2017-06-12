/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/PurgeAttachmentHandler.java $
 * $Id: PurgeAttachmentHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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

package org.etudes.archives.api;

/**
 * AttachmentHandler handles attachments.
 */
public interface PurgeAttachmentHandler
{
	/**
	 * Purge this attachment.
	 * 
	 * @param resourceRef
	 *        The content hosting reference to the attachment.
	 * @param deleteEmptyCollection
	 *        if true, and the attachment is alone in a collection, purge the collection too.
	 */
	void purgeAttachment(String resourceRef, boolean deleteEmptyCollection);

	/**
	 * Read the body of the attachment as UTF-8 character data into a String.
	 * 
	 * @param resourceRef
	 *        The content hosting reference to the attachment.
	 * @return The attachment body as a String, or null if not found.
	 */
	//String readAttachment(String resourceRef);
}
