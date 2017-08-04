====
    Copyright 2017 Smithsonian Institution.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License.You may obtain a copy of
    the License at: http://www.apache.org/licenses/

    This software and accompanying documentation is supplied without
    warranty of any kind. The copyright holder and the Smithsonian Institution:
    (1) expressly disclaim any warranties, express or implied, including but not
    limited to any implied warranties of merchantability, fitness for a
    particular purpose, title or non-infringement; (2) do not assume any legal
    liability or responsibility for the accuracy, completeness, or usefulness of
    the software; (3) do not represent that use of the software would not
    infringe privately owned rights; (4) do not warrant that the software
    is error-free or will be maintained, supported, updated or enhanced;
    (5) will not be liable for any indirect, incidental, consequential special
    or punitive damages of any kind or nature, including but not limited to lost
    profits or loss of data, on any basis arising from contract, tort or
    otherwise, even if any of the parties has been warned of the possibility of
    such loss or damage.

    This distribution includes several third-party libraries, each with their own
    license terms. For a complete copy of all copyright and license terms, including
    those of third-party libraries, please see the product release notes.
====

1. What is REPOSITORY-POLICIES-DIRECTORY?
REPOSITORY-POLICIES-DIRECTORY refers to the file system directory used to hold repository authorization policies.  
The directory which serves this function is configured in fedora.fcfg, using the Authorization module parameter of the same name.  
REPOSITORY-POLICIES-DIRECTORY is an arbitrary file system location, readable by Fedora server, and written to by Fedora server 
on first use of an installation.  (There are a few restrictions on REPOSITORY-POLICIES-DIRECTORY placement, as described below.)


2. How is REPOSITORY-POLICIES-DIRECTORY created and initially populated?
REPOSITORY-POLICIES-DIRECTORY is created if it doesn't already exist when Fedora server starts.    
Further, if a subdirectory REPOSITORY-POLICIES-DIRECTORY/default doesn't already exist when Fedora server starts, it is created and any 
default repository policies which come shipped with Fedora are copied into that subdirectory.  (With Fedora 2.1, default repository policies
approximate Fedora 2.0 protections.)
This subdirectory creation and optional policy copying usually occurs only the first time you run Fedora server after installing it.  
You can edit or delete these policies as explained below. 
REPOSITORY-POLICIES-DIRECTORY is not prepopulated by Fedora with policies directly under that directory itself.  This is because
this is where you add, and later edit or delete, repository policies of your -own- choosing, as explained below.


3. Do I need to create REPOSITORY-POLICIES-DIRECTORY myself?
No.  You -could- create the REPOSITORY-POLICIES-DIRECTORY directory yourself before you first run Fedora server.  This 
would, e.g., facilitate your putting your own repository policies in place immediately.  In practice, this is unnecessary, as 
you will likely go though a period of system tuning before production use.  Creating this directory yourself is -not- recommended.


4. Should I create REPOSITORY-POLICIES-DIRECTORY/default myself?
No.  If you yourself create the subdirectory REPOSITORY-POLICIES-DIRECTORY/default, this will prevent Fedora server from 
initializing properly with any default repository policies.  In the worst case, the Fedora server might not start at all, or 
might start but deny requests.  It is best to let Fedora do this initialization, which you can then adjust by optionally editing or 
deleting default policies. 


5. I see directories with default policies in Fedora's src and dist trees.  Why can't I simply point REPOSITORY-POLICIES-DIRECTORY into 
one of those directories? 
Default repository policies have backing copies in Fedora binary distributions in the Fedora dist tree, and have yet other copies 
in Fedora source distributions in the src tree, respectively in the two directories: 
.../dist/server/fedora-internal-use/fedora-internal-use-repository-policies and
.../src/xml/xacml-policies/default/default-repository-policies.
The contents of these two directories serve to build and initialize a Fedora installation, and are -not- intended
to be changed in any way at user installations (no file additions, nor edits, nor deletions).  

Pointing REPOSITORY-POLICIES-DIRECTORY into the src or dist trees could lead to editing the backing copies in order to configure 
policies actually in play.  To be clear, make changes -only- in the REPOSITORY-POLICIES-DIRECTORY directory as configured in fedora.fcfg:
the backing copies should be kept distinct and should -not- be changed.


6. Can I locate REPOSITORY-POLICIES-DIRECTORY somewhere else under FEDORA_HOME?
Placing REPOSITORY-POLICIES-DIRECTORY -anywhere- under FEDORA_HOME is probably unwise, as changes to the policies it holds could be 
easily lost on later upgrade to a new Fedora version.  We recommend placing REPOSITORY-POLICIES-DIRECTORY outside of the FEDORA_HOME tree.


7. How do I add a new (non-default) repository policy?
If you want to -add- your own repository policies, do so directly in the REPOSITORY-POLICIES-DIRECTORY directory,
i.e., -not- in the REPOSITORY-POLICIES-DIRECTORY/default directory.


8. Why can't I just add a new (non-default) repository policy to the REPOSITORY-POLICIES-DIRECTORY/default directory?
Reserving REPOSITORY-POLICIES-DIRECTORY/default for -default- repository policies will keep maintenance clean and tidy.


9. How do I change a default repository policy?
If a default repository policy needs adjustment for your local needs, you can edit any policy in REPOSITORY-POLICIES-DIRECTORY/default 
to achieve a similar but different effect.  Do this carefully only after cautious consideration and do this only in the 
directory REPOSITORY-POLICIES-DIRECTORY/default.


10. How do I remove a default repository policy?
If a default repository policy is simply inappropriate for your local needs, you can delete its policy file from 
REPOSITORY-POLICIES-DIRECTORY/default.    Do this carefully only after cautious consideration and do this only in the 
directory REPOSITORY-POLICIES-DIRECTORY/default.


11. Can I delete all of the default repository policies?
If none of the default repository policies make sense for your site, you can delete all of the default repository policy files
from REPOSITORY-POLICIES-DIRECTORY/default.  Fedora will leave this as-is, as you maintained it.  However, do -not- delete
the REPOSITORY-POLICIES-DIRECTORY/default subdirectory itself, as this would cause Fedora to re-initialize default repository policies 
on the next server (re-)start.


12. My changes have become confusing, and I want to start over.  How do I re-establish Fedora's repository policy defaults?
If you delete REPOSITORY-POLICIES-DIRECTORY/default itself (contents -and- directory), Fedora will re-initialize default repository 
policies on the next server (re-)start.

