--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/sql/mysql/archives_site_term.sql $
-- $Id: archives_site_term.sql 2839 2012-04-11 21:57:01Z ggolden $
--**********************************************************************************
--
-- Copyright (c) 2009 Etudes, Inc.
-- 
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
--*********************************************************************************/

-----------------------------------------------------------------------------
-- Term Handler DDL
-----------------------------------------------------------------------------

CREATE TABLE ARCHIVES_TERM
(
  ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  TERM VARCHAR(12) NOT NULL,
  SUFFIX VARCHAR(12),
  KEY ARCHIVES_TERM_IDX_T (TERM)
);

CREATE TABLE ARCHIVES_SITE_TERM
(
  ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  SITE_ID VARCHAR(99) NOT NULL,
  TERM_ID BIGINT UNSIGNED NOT NULL,
  KEY ARCHIVES_SITE_TERM_IDX_S (SITE_ID),
  KEY ARCHIVES_SITE_TERM_IDX_T (TERM_ID)
);

INSERT INTO ARCHIVES_TERM (TERM, SUFFIX) VALUES
('unknown', NULL),
('deleted', NULL),
('user', NULL),
('system', NULL),
('project', NULL),
('dev','DEV'),
('w06', 'W06'),
('sp06', 'SP06'),
('su06', 'SU06'),
('f06', 'F06'),
('w07', 'W07'),
('sp07', 'SP07'),
('su07', 'SU07'),
('f07', 'F07'),
('w08', 'W08'),
('sp08', 'SP08'),
('su08', 'SU08'),
('f08', 'F08'),
('w09', 'W09'),
('sp09', 'SP09'),
('su09', 'SU09'),
('f09', 'F09'),
('w10', 'W10'),
('sp10', 'SP10'),
('su10', 'SU10'),
('f10', 'F10'),
('w11', 'W11'),
('sp11', 'SP11'),
('su11', 'SU11'),
('f11', 'F11'),
('w12', 'W12'),
('sp12', 'SP12'),
('su12', 'SU12'),
('f12', 'F12'),
('w13', 'W13'),
('sp13', 'SP13'),
('su13', 'SU13'),
('f13', 'F13'),
('w14', 'W14'),
('sp14', 'SP14'),
('su14', 'SU14'),
('f14', 'F14'),
('w15', 'W15'),
('sp15', 'SP15'),
('su15', 'SU15'),
('f15', 'F15'),
('w16', 'W16'),
('sp16', 'SP16'),
('su16', 'SU16'),
('f16', 'F16'),
('w17', 'W17'),
('sp17', 'SP17'),
('su17', 'SU17'),
('f17', 'F17'),
('w18', 'W18'),
('sp18', 'SP18'),
('su18', 'SU18'),
('f18', 'F18'),
('w19', 'W19'),
('sp19', 'SP19'),
('su19', 'SU19'),
('f19', 'F19'),
('w20', 'W20'),
('sp20', 'SP20'),
('su20', 'SU20'),
('f20', 'F20'),
('w21', 'W21'),
('sp21', 'SP21'),
('su21', 'SU21'),
('f21', 'F21'),
('w22', 'W22'),
('sp22', 'SP22'),
('su22', 'SU22'),
('f22', 'F22');

insert into ARCHIVES_SITE_TERM (SITE_ID, TERM_ID)
select site_id, case
  when is_user = 1 then 3
  when is_special = 1 then 4
  when type = 'project' then 5
  when site_id = '!admin' then 4
  when site_id = 'mercury' then 4
  when title like '%DEV' then 6
  when title like '%W06' then 7
  when title like '%SP06' then 8
  when title like '%SU06' then 9
  when title like '%F06' then 10
  when title like '%W07' then 11
  when title like '%SP07' then 12
  when title like '%SU07' then 13
  when title like '%F07' then 14
  when title like '%W08' then 15
  when title like '%SP08' then 16
  when title like '%SU08' then 17
  when title like '%F08' then 18
  when title like '%W09' then 19
  when title like '%SP09' then 20
  when title like '%SU09' then 21
  when title like '%F09' then 22
  when title like '%W10' then 23
  when title like '%SP10' then 24
  when title like '%SU10' then 25
  when title like '%F10' then 26
  when title like '%W11' then 27
  when title like '%SP11' then 28
  when title like '%SU11' then 29
  when title like '%F11' then 30
  when title like '%W12' then 31
  when title like '%SP12' then 32
  when title like '%SU12' then 33
  when title like '%F12' then 34
  when title like '%W13' then 35
  when title like '%SP13' then 36
  when title like '%SU13' then 37
  when title like '%F13' then 38
  when title like '%W14' then 39
  when title like '%SP14' then 40
  when title like '%SU14' then 41
  when title like '%F14' then 42
  when title like '%W15' then 43
  when title like '%SP15' then 44
  when title like '%SU15' then 45
  when title like '%F15' then 46
  when title like '%W16' then 47
  when title like '%SP16' then 48
  when title like '%SU16' then 49
  when title like '%F16' then 50
  when title like '%W17' then 51
  when title like '%SP17' then 52
  when title like '%SU17' then 53
  when title like '%F17' then 54
  when title like '%W18' then 55
  when title like '%SP18' then 56
  when title like '%SU18' then 57
  when title like '%F18' then 58
  when title like '%W19' then 59
  when title like '%SP19' then 60
  when title like '%SU19' then 61
  when title like '%F19' then 62
  when title like '%W20' then 63
  when title like '%SP20' then 64
  when title like '%SU20' then 65
  when title like '%F20' then 66
  when title like '%W21' then 67
  when title like '%SP21' then 68
  when title like '%SU21' then 69
  when title like '%F21' then 70
  when title like '%W22' then 71
  when title like '%SP22' then 72
  when title like '%SU22' then 73
  when title like '%F22' then 74
  else 1 end as 'term'
from SAKAI_SITE;
