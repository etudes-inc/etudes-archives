/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/ImportHandler.java $
 * $Id: ImportHandler.java 2867 2012-04-26 02:28:28Z ggolden $
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

import java.util.Set;

/**
 * ImportHandler handles importing archived data for a specialized set of data.
 */
public interface ImportHandler
{
	/**
	 * Import the data for this site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact to import.
	 * @param archive
	 *        The archive the artifact came from.
	 * @param toolIds
	 *        The sakai tool ids for the tools the user selected for the import; if null, everything is selected.
	 */
	void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds);

	/**
	 * If the artifact will be imported, add its references to the archive's references.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact to import.
	 * @param archive
	 *        The archive the artifact came from.
	 * @param toolIds
	 *        The sakai tool ids for the tools the user selected for the import; if null, everything is selected.
	 */
	void registerFilteredReferences(String siteId, Artifact artifact, Archive archive, Set<String> toolIds);
}
