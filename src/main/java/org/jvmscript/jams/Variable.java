package org.jvmscript.jams;

/* sample variable output
{
   "acl": {
      "genericACL": []
   },
   "currentLength": 8,
   "dataType": "Text",
   "description": "IC File",
   "lastChangedBy": "CORCLEARING\\lferguson.admin",
   "lastChangeUTC": "2017-05-02T11:46:13.897Z",
   "parentFolderId": 5,
   "parentFolderName": "\\Inteliclear",
   "value": "test.txt",
   "variableId": 3,
   "variableName": "IcFile"
}
 */

import java.util.List;

public class Variable {
    public Acl acl;
    public Integer currentLength;
    public String dataType;
    public String description;
    public String lastChangedBy;
    public String lastChangeUTC;
    public Integer parentFolderId;
    public String parentFolderName;
    public String value;
    public Integer variableId;
    public String variableName;

}
