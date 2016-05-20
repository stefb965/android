package mega.privacy.android.app.lollipop.controllers;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.DownloadService;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MegaPreferences;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.lollipop.ContactsExplorerActivityLollipop;
import mega.privacy.android.app.lollipop.FileExplorerActivityLollipop;
import mega.privacy.android.app.lollipop.FileLinkActivityLollipop;
import mega.privacy.android.app.lollipop.FileStorageActivityLollipop;
import mega.privacy.android.app.lollipop.FolderLinkActivityLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.lollipop.ZipBrowserActivityLollipop;
import mega.privacy.android.app.utils.Constants;
import mega.privacy.android.app.utils.MegaApiUtils;
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop;
import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaNode;

public class NodeController {

    Context context;
    MegaApiAndroid megaApi;
    DatabaseHandler dbH;
    MegaPreferences prefs = null;

    public NodeController(Context context){
        log("NodeController created");
        this.context = context;
        if (megaApi == null){
            megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
        }
    }

    public void copyNodes(ArrayList<Long> handleList){
        log("showCopyLollipop");
        Intent intent = new Intent(context, FileExplorerActivityLollipop.class);
        intent.setAction(FileExplorerActivityLollipop.ACTION_PICK_COPY_FOLDER);
        long[] longArray = new long[handleList.size()];
        for (int i=0; i<handleList.size(); i++){
            longArray[i] = handleList.get(i);
        }
        intent.putExtra("COPY_FROM", longArray);
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_COPY_FOLDER);
    }

    public void moveNodes(ArrayList<Long> handleList){
        log("showMoveLollipop");
        Intent intent = new Intent(context, FileExplorerActivityLollipop.class);
        intent.setAction(FileExplorerActivityLollipop.ACTION_PICK_MOVE_FOLDER);
        long[] longArray = new long[handleList.size()];
        for (int i=0; i<handleList.size(); i++){
            longArray[i] = handleList.get(i);
        }
        intent.putExtra("MOVE_FROM", longArray);
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_MOVE_FOLDER);
    }

    public void sendToInbox(MegaNode node){
        log("sentToInbox MegaNode");

        ((ManagerActivityLollipop) context).setSendToInbox(true);

        Intent intent = new Intent(ContactsExplorerActivityLollipop.ACTION_PICK_CONTACT_SEND_FILE);
        intent.setClass(context, ContactsExplorerActivityLollipop.class);
        //Multiselect=0
        intent.putExtra("MULTISELECT", 0);
        intent.putExtra("SEND_FILE",1);
        intent.putExtra(ContactsExplorerActivityLollipop.EXTRA_NODE_HANDLE, node.getHandle());
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_CONTACT);
    }

    public void sendToInboxNodes(ArrayList<Long> handleList){
        log("sendToInboxNodes handleList");

        ((ManagerActivityLollipop) context).setSendToInbox(true);

        Intent intent = new Intent(ContactsExplorerActivityLollipop.ACTION_PICK_CONTACT_SEND_FILE);
        intent.setClass(context, ContactsExplorerActivityLollipop.class);
        long[] handles=new long[handleList.size()];
        int j=0;
        for(int i=0; i<handleList.size();i++){
            handles[j]=handleList.get(i);
            j++;
        }
        intent.putExtra("MULTISELECT", 1);
        intent.putExtra("SEND_FILE",1);
        log("handles length: "+handles.length);
        intent.putExtra(ContactsExplorerActivityLollipop.EXTRA_NODE_HANDLE, handles);
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_CONTACT);
    }

    //Old onFileClick
    public void prepareForDownload(ArrayList<Long> handleList){
        log("prepareForDownload: "+handleList.size()+" files to download");
        long size = 0;
        long[] hashes = new long[handleList.size()];
        for (int i=0;i<handleList.size();i++){
            hashes[i] = handleList.get(i);
            MegaNode nodeTemp = megaApi.getNodeByHandle(hashes[i]);
            if (nodeTemp != null){
                size += nodeTemp.getSize();
            }
        }
        log("Number of files: "+hashes.length);

        if (dbH == null){
//			dbH = new DatabaseHandler(getApplicationContext());
            dbH = DatabaseHandler.getDbHandler(context.getApplicationContext());
        }

        boolean askMe = true;
        boolean advancedDevices=false;
        String downloadLocationDefaultPath = Util.downloadDIR;
        prefs = dbH.getPreferences();
        if (prefs != null){
            log("prefs != null");
            if (prefs.getStorageAskAlways() != null){
                if (!Boolean.parseBoolean(prefs.getStorageAskAlways())){
                    log("askMe==false");
                    if (prefs.getStorageDownloadLocation() != null){
                        if (prefs.getStorageDownloadLocation().compareTo("") != 0){
                            askMe = false;
                            downloadLocationDefaultPath = prefs.getStorageDownloadLocation();
                        }
                    }
                }
                else
                {
                    log("askMe==true");
                    //askMe=true
                    if (prefs.getStorageAdvancedDevices() != null){
                        advancedDevices = Boolean.parseBoolean(prefs.getStorageAdvancedDevices());
                    }

                }
            }
        }

        if (askMe){
            log("askMe");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                File[] fs = context.getExternalFilesDirs(null);
                if (fs.length > 1){
                    if (fs[1] == null){
                        Intent intent = new Intent(FileStorageActivityLollipop.Mode.PICK_FOLDER.getAction());
                        intent.putExtra(FileStorageActivityLollipop.EXTRA_FROM_SETTINGS, false);
                        intent.putExtra(FileStorageActivityLollipop.EXTRA_SIZE, size);
                        intent.setClass(context, FileStorageActivityLollipop.class);
                        intent.putExtra(FileStorageActivityLollipop.EXTRA_DOCUMENT_HASHES, hashes);
                        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_LOCAL_FOLDER);
                    }
                    else{
                        Dialog downloadLocationDialog;
                        String[] sdCardOptions = context.getResources().getStringArray(R.array.settings_storage_download_location_array);
                        AlertDialog.Builder b=new AlertDialog.Builder(context);

                        b.setTitle(context.getResources().getString(R.string.settings_storage_download_location));
                        final long sizeFinal = size;
                        final long[] hashesFinal = new long[hashes.length];
                        for (int i=0; i< hashes.length; i++){
                            hashesFinal[i] = hashes[i];
                        }

                        b.setItems(sdCardOptions, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch(which){
                                    case 0:{
                                        Intent intent = new Intent(FileStorageActivityLollipop.Mode.PICK_FOLDER.getAction());
                                        intent.putExtra(FileStorageActivityLollipop.EXTRA_FROM_SETTINGS, false);
                                        intent.putExtra(FileStorageActivityLollipop.EXTRA_SIZE, sizeFinal);
                                        intent.setClass(context, FileStorageActivityLollipop.class);
                                        intent.putExtra(FileStorageActivityLollipop.EXTRA_DOCUMENT_HASHES, hashesFinal);
                                        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_LOCAL_FOLDER);
                                        break;
                                    }
                                    case 1:{
                                        File[] fs = context.getExternalFilesDirs(null);
                                        if (fs.length > 1){
                                            String path = fs[1].getAbsolutePath();
                                            File defaultPathF = new File(path);
                                            defaultPathF.mkdirs();
                                            ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.general_download) + ": "  + defaultPathF.getAbsolutePath());
                                            checkSizeBeforeDownload(path, null, sizeFinal, hashesFinal);
                                        }
                                        break;
                                    }
                                }
                            }
                        });
                        b.setNegativeButton(context.getResources().getString(R.string.general_cancel), new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                        downloadLocationDialog = b.create();
                        downloadLocationDialog.show();
                    }
                }
                else{
                    Intent intent = new Intent(FileStorageActivityLollipop.Mode.PICK_FOLDER.getAction());
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_FROM_SETTINGS, false);
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_SIZE, size);
                    intent.setClass(context, FileStorageActivityLollipop.class);
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_DOCUMENT_HASHES, hashes);
                    ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_LOCAL_FOLDER);
                }
            }
            else{
                if(advancedDevices){
                    log("advancedDevices");
                    //Launch Intent to SAF
                    if(hashes.length==1){
                        downloadLocationDefaultPath = prefs.getStorageDownloadLocation();
                        this.openAdvancedDevices(hashes[0]);
                    }
                    else
                    {
                        ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.context_select_one_file));

                    }
                }
                else{
                    log("NOT advancedDevices");
                    Intent intent = new Intent(FileStorageActivityLollipop.Mode.PICK_FOLDER.getAction());
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_FROM_SETTINGS, false);
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_SIZE, size);
                    intent.setClass(context, FileStorageActivityLollipop.class);
                    intent.putExtra(FileStorageActivityLollipop.EXTRA_DOCUMENT_HASHES, hashes);
                    ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_LOCAL_FOLDER);
                }
            }
        }
        else{
            log("NOT askMe");
            File defaultPathF = new File(downloadLocationDefaultPath);
            defaultPathF.mkdirs();
            checkSizeBeforeDownload(downloadLocationDefaultPath, null, size, hashes);
        }
    }

    //Old downloadTo
    public void checkSizeBeforeDownload(String parentPath, String url, long size, long [] hashes){
        //Variable size is incorrect for folders, it is always -1 -> sizeTemp calculates the correct size
        log("checkSizeBeforeDownload - parentPath: "+parentPath+ " url: "+url+" size: "+size);
        log("files to download: "+hashes.length);
        log("SIZE to download before calculating: "+size);

        final String parentPathC = parentPath;
        final String urlC = url;
        final long [] hashesC = hashes;
        long sizeTemp=0;

        for (long hash : hashes) {
            MegaNode node = megaApi.getNodeByHandle(hash);
            if(node!=null){
                if(node.isFolder()){
                    log("node to download is FOLDER");
                    sizeTemp=sizeTemp+ MegaApiUtils.getFolderSize(node, context);
                }
                else{
                    sizeTemp = sizeTemp+node.getSize();
                }
            }
        }

        final long sizeC = sizeTemp;
        log("the final size is: "+Util.getSizeString(sizeTemp));

        //Check if there is available space
        double availableFreeSpace = Double.MAX_VALUE;
        try{
            StatFs stat = new StatFs(parentPath);
            availableFreeSpace = (double)stat.getAvailableBlocks() * (double)stat.getBlockSize();
        }
        catch(Exception ex){}

        if(availableFreeSpace < size) {
            ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.error_not_enough_free_space));
            log("Not enough space");
            return;
        }

        String ask=dbH.getAttributes().getAskSizeDownload();

        if(ask==null){
            ask="true";
        }

        if(ask.equals("false")){
            log("SIZE: Do not ask before downloading");
            checkInstalledAppBeforeDownload(parentPathC, urlC, sizeC, hashesC);
        }
        else{
            log("SIZE: Ask before downloading");
            //Check size to download
            //100MB=104857600
            //10MB=10485760
            //1MB=1048576
            if(sizeC>104857600){
                log("Show size confirmacion: "+sizeC);
                //Show alert
                ((ManagerActivityLollipop) context).askSizeConfirmationBeforeDownload(parentPathC, urlC, sizeC, hashesC);
            }
            else{
                checkInstalledAppBeforeDownload(parentPathC, urlC, sizeC, hashesC);
            }
        }
    }

    //Old proceedToDownload
    public void checkInstalledAppBeforeDownload(String parentPath, String url, long size, long [] hashes){
        log("checkInstalledAppBeforeDownload");
        boolean confirmationToDownload = false;
        final String parentPathC = parentPath;
        final String urlC = url;
        final long sizeC = size;
        final long [] hashesC = hashes;

        String ask=dbH.getAttributes().getAskNoAppDownload();

        if(ask==null){
            log("ask==null");
            ask="true";
        }

        if(ask.equals("false")){
            log("INSTALLED APP: Do not ask before downloading");
            download(parentPathC, urlC, sizeC, hashesC);
        }
        else{
            log("INSTALLED APP: Ask before downloading");
            if (hashes != null){
                for (long hash : hashes) {
                    MegaNode node = megaApi.getNodeByHandle(hash);
                    log("Node: "+ node.getName());

                    if(node.isFile()){
                        Intent checkIntent = new Intent(Intent.ACTION_VIEW, null);
                        log("MimeTypeList: "+ MimeTypeList.typeForName(node.getName()).getType());

                        checkIntent.setType(MimeTypeList.typeForName(node.getName()).getType());

                        try{
                            if (!MegaApiUtils.isIntentAvailable(context, checkIntent)){
                                confirmationToDownload = true;
//                                nodeToDownload=node.getName();
                                break;
                            }
                        }catch(Exception e){
                            log("isIntent EXCEPTION");
                            confirmationToDownload = true;
//                            nodeToDownload=node.getName();
                            break;
                        }
                    }
                }
            }

            //Check if show the alert message
            if(confirmationToDownload){
                //Show message
                ((ManagerActivityLollipop) context).askConfirmationNoAppInstaledBeforeDownload(parentPathC, urlC, sizeC, hashesC);
            }
            else{
                download(parentPathC, urlC, sizeC, hashesC);
            }
        }
    }

    public void download(String parentPath, String url, long size, long [] hashes){
        log("download-----------");
        log("downloadTo, parentPath: "+parentPath+ "url: "+url+" size: "+size);
        log("files to download: "+hashes.length);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasStoragePermission = (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
            if (!hasStoragePermission) {
                ActivityCompat.requestPermissions(((ManagerActivityLollipop) context),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        ManagerActivityLollipop.REQUEST_WRITE_STORAGE);
            }
        }

        if (hashes != null){
            for (long hash : hashes) {
                MegaNode node = megaApi.getNodeByHandle(hash);
                log("Node: "+ node.getName());
            }
        }

        if (hashes == null){
            log("hashes is null");
            if(url != null) {
                log("url NOT null");
                Intent service = new Intent(context, DownloadService.class);
                service.putExtra(DownloadService.EXTRA_URL, url);
                service.putExtra(DownloadService.EXTRA_SIZE, size);
                service.putExtra(DownloadService.EXTRA_PATH, parentPath);
                context.startService(service);
            }
        }
        else{
            log("hashes is NOT null");
            if(hashes.length == 1){
                log("hashes.length == 1");
                MegaNode tempNode = megaApi.getNodeByHandle(hashes[0]);

                if((tempNode != null) && tempNode.getType() == MegaNode.TYPE_FILE){
                    log("ISFILE");
                    String localPath = Util.getLocalFile(context, tempNode.getName(), tempNode.getSize(), parentPath);
                    //Check if the file is already downloaded
                    if(localPath != null){
                        log("localPath != null");
                        try {
                            log("Call to copyFile: localPath: "+localPath+" node name: "+tempNode.getName());
                            Util.copyFile(new File(localPath), new File(parentPath, tempNode.getName()));

                            if(Util.isVideoFile(parentPath+"/"+tempNode.getName())){
                                log("Is video!!!");
//								MegaNode videoNode = megaApi.getNodeByHandle(tempNode.getNodeHandle());
                                if (tempNode != null){
                                    if(!tempNode.hasThumbnail()){
                                        log("The video has not thumb");
                                        ThumbnailUtilsLollipop.createThumbnailVideo(context, localPath, megaApi, tempNode.getHandle());
                                    }
                                }
                            }
                            else{
                                log("NOT video!");
                            }
                        }
                        catch(Exception e) {
                            log("Exception!!");
                        }

                        if(MimeTypeList.typeForName(tempNode.getName()).isZip()){
                            log("MimeTypeList ZIP");
                            File zipFile = new File(localPath);

                            Intent intentZip = new Intent();
                            intentZip.setClass(context, ZipBrowserActivityLollipop.class);
                            intentZip.putExtra(ZipBrowserActivityLollipop.EXTRA_PATH_ZIP, zipFile.getAbsolutePath());
                            intentZip.putExtra(ZipBrowserActivityLollipop.EXTRA_HANDLE_ZIP, tempNode.getHandle());

                            context.startActivity(intentZip);

                        }
                        else {
                            log("MimeTypeList other file");
                            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                            viewIntent.setDataAndType(Uri.fromFile(new File(localPath)), MimeTypeList.typeForName(tempNode.getName()).getType());
                            if (MegaApiUtils.isIntentAvailable(context, viewIntent)) {
                                log("if isIntentAvailable");
                                context.startActivity(viewIntent);
                            } else {
                                log("ELSE isIntentAvailable");
                                Intent intentShare = new Intent(Intent.ACTION_SEND);
                                intentShare.setDataAndType(Uri.fromFile(new File(localPath)), MimeTypeList.typeForName(tempNode.getName()).getType());
                                if (MegaApiUtils.isIntentAvailable(context, intentShare)) {
                                    log("call to startActivity(intentShare)");
                                    context.startActivity(intentShare);
                                }
                                ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.general_already_downloaded));
                            }
                        }
                        return;
                    }
                    else{
                        log("localPath is NULL");
                    }
                }
            }

            for (long hash : hashes) {
                log("hashes.length more than 1");
                MegaNode node = megaApi.getNodeByHandle(hash);
                if(node != null){
                    log("node NOT null");
                    Map<MegaNode, String> dlFiles = new HashMap<MegaNode, String>();
                    if (node.getType() == MegaNode.TYPE_FOLDER) {
                        log("MegaNode.TYPE_FOLDER");
                        getDlList(dlFiles, node, new File(parentPath, new String(node.getName())));
                    } else {
                        log("MegaNode.TYPE_FILE");
                        dlFiles.put(node, parentPath);
                    }

                    for (MegaNode document : dlFiles.keySet()) {

                        String path = dlFiles.get(document);
                        log("path of the file: "+path);
                        log("start service");
                        Intent service = new Intent(context, DownloadService.class);
                        service.putExtra(DownloadService.EXTRA_HASH, document.getHandle());
                        service.putExtra(DownloadService.EXTRA_URL, url);
                        service.putExtra(DownloadService.EXTRA_SIZE, document.getSize());
                        service.putExtra(DownloadService.EXTRA_PATH, path);
                        context.startService(service);
                    }
                }
                else if(url != null) {
                    log("URL NOT null");
                    log("start service");
                    Intent service = new Intent(context, DownloadService.class);
                    service.putExtra(DownloadService.EXTRA_HASH, hash);
                    service.putExtra(DownloadService.EXTRA_URL, url);
                    service.putExtra(DownloadService.EXTRA_SIZE, size);
                    service.putExtra(DownloadService.EXTRA_PATH, parentPath);
                    context.startService(service);
                }
                else {
                    log("node NOT fOUND!!!!!");
                }
            }
        }
    }

    /*
	 * Get list of all child files
	 */
    private void getDlList(Map<MegaNode, String> dlFiles, MegaNode parent, File folder) {
        log("getDlList");
        if (megaApi.getRootNode() == null)
            return;

        folder.mkdir();
        ArrayList<MegaNode> nodeList = megaApi.getChildren(parent);
        for(int i=0; i<nodeList.size(); i++){
            MegaNode document = nodeList.get(i);
            if (document.getType() == MegaNode.TYPE_FOLDER) {
                File subfolder = new File(folder, new String(document.getName()));
                getDlList(dlFiles, document, subfolder);
            }
            else {
                dlFiles.put(document, folder.getAbsolutePath());
            }
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void openAdvancedDevices (long handle){
        log("openAdvancedDevices");
        String externalPath = Util.getExternalCardPath();

        if(externalPath!=null){
            log("ExternalPath for advancedDevices: "+externalPath);
            MegaNode node = megaApi.getNodeByHandle(handle);
            if(node!=null){

//				File newFile =  new File(externalPath+"/"+node.getName());
                File newFile =  new File(node.getName());
                log("File: "+newFile.getPath());
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Create a file with the requested MIME type.
                String mimeType = MimeTypeList.getMimeType(newFile);
                log("Mimetype: "+mimeType);
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_TITLE, node.getName());
                intent.putExtra("handleToDownload", handle);
                try{
                    ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.WRITE_SD_CARD_REQUEST_CODE);
                }
                catch(Exception e){
                    log("Exception in External SDCARD");
                    Environment.getExternalStorageDirectory();
                    ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.no_external_SD_card_detected));
                }
            }
        }
        else{
            log("No external SD card");
            Environment.getExternalStorageDirectory();
            ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.no_external_SD_card_detected));
        }
    }

    public void renameNode(MegaNode document, String newName){
        log("renameNode");
        if (newName.compareTo(document.getName()) == 0) {
            return;
        }

        if(!Util.isOnline(context)){
            ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.error_server_connection_problem));
            return;
        }

        log("renaming " + document.getName() + " to " + newName);

        megaApi.renameNode(document, newName, ((ManagerActivityLollipop) context));
    }

    public void importLink(String url) {

        try {
            url = URLDecoder.decode(url, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {}
        url.replace(' ', '+');
        if(url.startsWith("mega://")){
            url = url.replace("mega://", "https://mega.co.nz/");
        }

        log("url " + url);

        // Download link
        if (url != null && (url.matches("^https://mega.co.nz/#!.*!.*$") || url.matches("^https://mega.nz/#!.*!.*$"))) {
            log("open link url");

//			Intent openIntent = new Intent(this, ManagerActivityLollipop.class);
            Intent openFileIntent = new Intent(context, FileLinkActivityLollipop.class);
            openFileIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openFileIntent.setAction(ManagerActivityLollipop.ACTION_OPEN_MEGA_LINK);
            openFileIntent.setData(Uri.parse(url));
            ((ManagerActivityLollipop) context).startActivity(openFileIntent);
//			finish();
            return;
        }

        // Folder Download link
        else if (url != null && (url.matches("^https://mega.co.nz/#F!.+$") || url.matches("^https://mega.nz/#F!.+$"))) {
            log("folder link url");
            Intent openFolderIntent = new Intent(context, FolderLinkActivityLollipop.class);
            openFolderIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openFolderIntent.setAction(ManagerActivityLollipop.ACTION_OPEN_MEGA_FOLDER_LINK);
            openFolderIntent.setData(Uri.parse(url));
            ((ManagerActivityLollipop) context).startActivity(openFolderIntent);
//			finish();
            return;
        }
        else{
            log("wrong url");
            Intent errorIntent = new Intent(context, ManagerActivityLollipop.class);
            errorIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ((ManagerActivityLollipop) context).startActivity(errorIntent);
        }
    }

    //old getPublicLinkAndShareIt
    public void exportLink(MegaNode document){
        log("exportLink");
        if (!Util.isOnline(context)){
            ((ManagerActivityLollipop) context).showSnackbar(context.getString(R.string.error_server_connection_problem));
            return;
        }
        ((ManagerActivityLollipop) context).setIsGetLink(true);
        megaApi.exportNode(document, ((ManagerActivityLollipop) context));
    }


    public void shareFolders(ArrayList<Long> handleList){
        log("shareFolders ArrayListLong");
        //TODO shareMultipleFolders

        Intent intent = new Intent(ContactsExplorerActivityLollipop.ACTION_PICK_CONTACT_SHARE_FOLDER);
        intent.setClass(context, ContactsExplorerActivityLollipop.class);

        long[] handles=new long[handleList.size()];
        int j=0;
        for(int i=0; i<handleList.size();i++){
            handles[j]=handleList.get(i);
            j++;
        }
        intent.putExtra(ContactsExplorerActivityLollipop.EXTRA_NODE_HANDLE, handles);
        //Multiselect=1 (multiple folders)
        intent.putExtra("MULTISELECT", 1);
        intent.putExtra("SEND_FILE",0);
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_CONTACT);
    }

    public void shareFolder(MegaNode node){
        log("shareFolder");

        Intent intent = new Intent(ContactsExplorerActivityLollipop.ACTION_PICK_CONTACT_SHARE_FOLDER);
        intent.setClass(context, ContactsExplorerActivityLollipop.class);
        //Multiselect=0
        intent.putExtra("MULTISELECT", 0);
        intent.putExtra("SEND_FILE",0);
        intent.putExtra(ContactsExplorerActivityLollipop.EXTRA_NODE_HANDLE, node.getHandle());
        ((ManagerActivityLollipop) context).startActivityForResult(intent, Constants.REQUEST_CODE_SELECT_CONTACT);
    }

    public static void log(String message) {
        Util.log("NodeController", message);
    }
}