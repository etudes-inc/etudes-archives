/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/ArchiveDescription.java $
 * $Id: ArchiveDescription.java 2827 2012-04-05 02:06:31Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012 Etudes, Inc.
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

import java.util.Date;

/**
 * ArchiveDescription describes an archive.
 */
public interface ArchiveDescription
{
	/**
	 * Access the date the archive was made.
	 * 
	 * @return The archive date.
	 */
	Date getDate();

	/**
	 * Access the archives id.
	 * 
	 * @return The archives id.
	 */
	Long getId();

	/**
	 * Access the site id of the site archived.
	 * 
	 * @return The site id.
	 */
	String getSiteId();

	/**
	 * Access the term id of the site archived.
	 * 
	 * @return The site's term id.
	 */
	Long getTerm();

	/**
	 * Access the human readable term of the site archived.
	 * 
	 * @return The site's term in human readable form.
	 */
	String getTermDescription();

	/**
	 * Access the title of the site archived.
	 * 
	 * @return The site's title.
	 */
	String getTitle();

	/**
	 * Check if this tool's data exists in the archice.
	 * 
	 * @param commonToolId
	 *        The tool id (String, such as sakai.chat, not a tool configuration / placement uuid) to search for.
	 * @return true if the tool's data exists in the archive, false if not.
	 */
	boolean hasTool(String commonToolId);
}
