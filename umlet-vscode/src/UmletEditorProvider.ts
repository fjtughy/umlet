// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import {WebviewPanel, workspace, WorkspaceConfiguration} from 'vscode';
import * as path from 'path';
import * as fs from "fs";
import * as parser from 'fast-xml-parser';
import {DebugLevel} from "./main/typescript/DebugLevel";

export var currentlyActivePanel: WebviewPanel | null = null;
let lastCurrentlyActivePanel: WebviewPanel | null = null; //always saves last panel which was active, even if its not in focus anymore. used for export commands and edit->copy/paste/cut
//always saves last panel which was active, even if its not in focus anymore. used for export commands and edit->copy/paste/cut, and ALSO checks if there is currently an active text window.
//this is to avoid triggering commands if a user switched to another text based tab. be aware that this will still trigger if a user switches to another custom tab instead.
export function lastCurrentlyActivePanelPurified(): WebviewPanel | null {
    console.log("current active text editor is: " + vscode.window.activeTextEditor);
    console.log("current active custom editor is: " + lastCurrentlyActivePanel?.active);
    if (lastCurrentlyActivePanel?.active) {
        return lastCurrentlyActivePanel;
    } else {
        return null;
    }
}

//lastCurrentlyActivePanel will be set to null after loosing focus for too long
//exportCurrentlyActivePanel always keeps track of the last active panel, so exporting functionality is available for longer than 1.5 seconds
export let exportCurrentlyActivePanel: WebviewPanel | null = null;

let lastChangeTriggeredByUri = ""; //whenever a document change is triggered by an instance of the UMLet Gwt application, the uri of the corresponding document will be tracked here.

export class UmletEditorProvider implements vscode.CustomTextEditorProvider {

    private static outputChannel: vscode.OutputChannel = vscode.window.createOutputChannel('UMLet');
    private static debugLevel: number | undefined = UmletEditorProvider.getConfiguration().get<number>('debugLevel');
    private static theme: string | undefined = UmletEditorProvider.getConfiguration().get<string>('theme');
    private static isPropertyPanelFocus: boolean = false;

    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly port: number
    ) {
    }

    /**
     * Called when our custom editor is opened.
     */
    resolveCustomTextEditor(document: vscode.TextDocument, webviewPanel: vscode.WebviewPanel, token: vscode.CancellationToken): void | Thenable<void> {
        //whenever the .uxf file is changed (for example throough a text editor in vs code), these changes shoule be reflected in umlet
        const changeDocumentSubscription = vscode.workspace.onDidChangeTextDocument(e => {
            console.log("DOCCHANGE" + e.contentChanges.length);
            //console.log("text document changed!, last change  uri:" + lastChangeTriggeredByUri + "       " + e.document.uri.toString() + "     " + document.uri.toString());
            //everytime something is changed by the gwt application, lastChangeTriggeredByUri will be set to the according document uri.
            //this is used to avoid a reset when the last change came directly from the gwt application, which would de-select current elements
            //currentlyActivePanel has to be checked, otherwise this will only be triggered for the first opened editor. we only want it on the active

            if (lastChangeTriggeredByUri === document.uri.toString() && webviewPanel === currentlyActivePanel) {
                console.log("last change came from the gwt application" + e.contentChanges.length);
                lastChangeTriggeredByUri = "";
            } else {
                //only trigger for right document
                //if e.contentChanges.length === 0, then there was no actual content change, but the grey dirty indicator was set by vs code
                //in that case we do not want to set gwt again, because that would unselect all selected elements
                if (e.document.uri.toString() === document.uri.toString() && e.contentChanges.length !== 0) {
                    UmletEditorProvider.postLog(DebugLevel.STANDARD, "match text change, injecting changes to gwt ");
                    UmletEditorProvider.postLog(DebugLevel.STANDARD, 'webview panel is: ' + webviewPanel);
                    webviewPanel.webview.postMessage({
                        command: 'myUpdate',
                        text: document.getText()
                    });
                }
            }
        });
        this.context.subscriptions.push(changeDocumentSubscription);

        this.prepareActivePanel(webviewPanel, document);
        // Extracting version from POM
        // It might be better to extract it from package.json once we've synchronized all version numbers   
        const pomString = fs.readFileSync(this.context.extensionPath + '/pom.xml').toString();
        const pom = parser.parse(pomString);
        const buildFolder = pom.project['artifactId'] + '-' + pom.project.parent['version'];

        webviewPanel.webview.options = {
            enableScripts: true,
            localResourceRoots: [vscode.Uri.file(path.join(this.context.extensionPath, 'target', buildFolder))]
        };

        // Handle messages from the webview
        webviewPanel.webview.onDidReceiveMessage(message => {
            switch (message.command) {
                case 'exportUxf':
                    this.saveFile(message.text);
                    return;
                case 'updateFiledataUxf':
                    this.updateCurrentFile(message.text, document);
                    return;
                case 'exportPng':
                    var actual_data = message.text.replace("data:image/png;base64,", "");
                    this.saveFileDecodePng(actual_data);
                    return;
                case 'exportPdf':
                    var actual_data = message.text.replace("data:application/octet-stream;base64,", "");
                    this.saveFileDecodePdf(actual_data);
                    return;
                case 'postLog':
                    UmletEditorProvider.postLog(DebugLevel.STANDARD, message.text);
                    return;
                case 'setClipboard':
                    vscode.env.clipboard.writeText(message.text);
                    return;
                case 'requestPasteClipboard':
                    vscode.env.clipboard.readText().then((text) => {
                        let clipboard_content = text;
                        UmletEditorProvider.postLog(DebugLevel.DETAILED, 'MESSAGE Paste, content is: ' + clipboard_content);
                        currentlyActivePanel?.webview.postMessage({
                            command: 'paste-response',
                            text: clipboard_content
                        });
                    });
                    return;
                case 'propertiesFocus':
                    UmletEditorProvider.isPropertyPanelFocus = JSON.parse(message.text);
                    return;
            }
        }, undefined, this.context.subscriptions);

        webviewPanel.onDidChangeViewState(event => {
            if (event.webviewPanel.active) {
                this.prepareActivePanel(webviewPanel, document);
            } else {
                //for some reason, onBlur gets called when a panel which was already opened gets opened.
                //this is not expected and leads to weird behaviour with tracking currently active panel, so its prevented
                UmletEditorProvider.postLog(DebugLevel.DETAILED, "blur TT");
                if (currentlyActivePanel === webviewPanel) {
                    currentlyActivePanel = null;
                    UmletEditorProvider.postLog(DebugLevel.DETAILED, 'panel ' + document.fileName + ' blurred, 1.5 seconds until last panel is reset');
                    setTimeout(resetLastPanel, 1500);
                } else {
                    UmletEditorProvider.postLog(DebugLevel.DETAILED, 'timer for ' + document.fileName + ' was not set, there is another currently active panel');
                }
                return;
            }
        });

        //called after 1.5 secs
        function resetLastPanel() {
            //only reset if this panel was the last currently active panel
            if (lastCurrentlyActivePanel === webviewPanel && currentlyActivePanel === null) {
                lastCurrentlyActivePanel = null;
                UmletEditorProvider.postLog(DebugLevel.DETAILED, 'last active panel for ' + document.fileName + ' was reset because time and its still the last active panel');
            } else {
                UmletEditorProvider.postLog(DebugLevel.DETAILED, 'last active panel for ' + document.fileName + ' was NOT reset because there now is another last active panel anyway');
            }

        }

        // Web-based IRI for retrieving ressources
        const webLocation = 'http://localhost:' + this.port;

        let fileContents = document.getText().toString();

        if (UmletEditorProvider.theme === undefined) {
            UmletEditorProvider.theme = 'VS Code setting';
        }

        webviewPanel.webview.html = this.getUmletWebviewPage(webLocation, fileContents.toString(), UmletEditorProvider.theme);
    }

    prepareActivePanel(webviewPanel: vscode.WebviewPanel, document: vscode.TextDocument) {
        UmletEditorProvider.postLog(DebugLevel.DETAILED, 'focus TT');
        if (webviewPanel.active) {
            UmletEditorProvider.postLog(DebugLevel.DETAILED, 'Webview ' + document.fileName + ' now focused because tt focus and is active');
            currentlyActivePanel = webviewPanel;
            exportCurrentlyActivePanel = webviewPanel; //this never gets reset and is always on the last panel which was active

            /*
            set last active panel, but only for 1.5 seconds
            Lazy copy/paste is only available during 1.5 seconds after the UMLet editor looses focus.
            this is to avoid situations where a user goes into the console to write something and uses copy/cut/paste, this would result in the copy/cut/paste command to be triggered
            in both umlet and the console. same with the command search function.
            */
            UmletEditorProvider.postLog(DebugLevel.DETAILED, 'last current active panel set, ' + document.fileName);
            lastCurrentlyActivePanel = webviewPanel;
        } else {
            UmletEditorProvider.postLog(DebugLevel.DETAILED, 'Webview ' + document.fileName + ' NOT FOCUSED because not active');
        }

        UmletEditorProvider.debugLevel = UmletEditorProvider.getConfiguration().get<number>('debugLevel');
        currentlyActivePanel?.webview.postMessage({
            command: 'debugLevel',
            text: UmletEditorProvider.debugLevel
        });
        this.updateTheme();
        this.updateFonts();
    }

    updateTheme() {
        let themeSetting = UmletEditorProvider.getConfiguration().get<string>('theme');
        if (UmletEditorProvider.theme === themeSetting) {
            return;
        }
        if (themeSetting === undefined) {
            themeSetting = 'VS Code setting';
        }
        UmletEditorProvider.theme = themeSetting;
        currentlyActivePanel?.webview.postMessage({
            command: 'themeSetting',
            text: themeSetting
        });
    }

    updateFonts() {
        let fonts = UmletEditorProvider.getFontSettingsData();
        currentlyActivePanel?.webview.postMessage({
            command: 'changeFont',
            text: fonts.join()
        });
    }

    static getFontSettingsData(): string[] {
        let fontNormalSetting = UmletEditorProvider.getConfiguration().get<string>('fontNormal');
        let fontItalicSetting = UmletEditorProvider.getConfiguration().get<string>('fontItalic');
        let fontBoldSetting = UmletEditorProvider.getConfiguration().get<string>('fontBold');

        let fonts: string[] = [];

        if (fontNormalSetting !== undefined && fontNormalSetting !== '') {
            fonts.push('normal@' + UmletEditorProvider.readFileAsBase64(fontNormalSetting, 'normal'));
        } else {
            fonts.push('normal@');
        }
        if (fontItalicSetting !== undefined && fontItalicSetting !== '') {
            fonts.push('italic@' + UmletEditorProvider.readFileAsBase64(fontItalicSetting, 'italic'));
        } else {
            fonts.push('italic@');
        }
        if (fontBoldSetting !== undefined && fontBoldSetting !== '') {
            fonts.push('bold@' + UmletEditorProvider.readFileAsBase64(fontBoldSetting, 'bold'));
        } else {
            fonts.push('bold@');
        }
        return fonts;
    }

    static readFileAsBase64(fileLocation: string, fontType: string): string {
        try {
            let fontFile = fs.readFileSync(fileLocation);
            if (UmletEditorProvider.isFontFileTtf(fontFile)) {
                return 'ttf@' + fontFile.toString('base64');
            }
            if (UmletEditorProvider.isFontFileOtf(fontFile)) {
                return 'otf@' + fontFile.toString('base64');
            }
        } catch (error) {
            // Is a directory or non existent file
        }
        UmletEditorProvider.postLog(DebugLevel.STANDARD, 'Provided ' + fontType + ' font file is neither a TTF nor a OTF file!');
        return '';
    }

    static isFontFileTtf(file: Buffer): boolean {
        let ttf_signature: number[] = [0, 1, 0, 0, 0];
        for (let i = 0; i < 5; i++) {
            if (file.readUInt8(i) !== ttf_signature[i]) {
                return false;
            }
        }
        return true;
    }

    static isFontFileOtf(file: Buffer): boolean {
        let otf_signature: number[] = [79, 84, 84, 79, 0];
        for (let i = 0; i < 5; i++) {
            if (file.readUInt8(i) !== otf_signature[i]) {
                return false;
            }
        }
        return true;
    }

    //gets the updated filedata from the webview if anything has changed
    updateCurrentFile(fileContent: string, document: vscode.TextDocument) {
        // Only update file state if there actually has been a change
        if (document.getText() !== fileContent) {
            lastChangeTriggeredByUri = document.uri.toString(); //used to avoid ressetting the webview if a change was triggered by the webview anyway
            UmletEditorProvider.postLog(DebugLevel.DETAILED, 'lastChangeTriggeredByUri set to: ' + lastChangeTriggeredByUri);
            const edit = new vscode.WorkspaceEdit();

            edit.replace(
                document.uri,
                new vscode.Range(0, 0, document.lineCount, 0),
                fileContent);

            vscode.workspace.applyEdit(edit);
        }
    }

    //shows popup savefile dialog for uxf files
    saveFile(fileContent: string) {
        vscode.window.showSaveDialog({
            filters: {
                'UML Diagram': ['uxf']
            }
        })
            .then(fileInfos => {
                if (fileInfos !== undefined) {
                    fs.writeFile(fileInfos.fsPath, fileContent, function (err) {
                        if (err) {
                            return console.log(err);
                        }
                    });
                }
            });
    }

    //shows popup savefile dialog for png files
    saveFileDecodePng(fileContent: string) {
        vscode.window.showSaveDialog({
            filters: {
                'Image': ['png']
            }
        })
            .then(fileInfos => {
                if (fileInfos !== undefined) {
                    fs.writeFile(fileInfos.fsPath, fileContent, {encoding: 'base64'}, function (err) {
                        if (err) {
                            return console.log(err);
                        }
                    });
                }
            });
    }

    //shows popup savefile dialog for PDF files
    saveFileDecodePdf(fileContent: string) {
        vscode.window.showSaveDialog({
            filters: {
                'PDF': ['pdf']
            }
        })
            .then(fileInfos => {
                if (fileInfos !== undefined) {
                    fs.writeFile(fileInfos.fsPath, fileContent, {encoding: 'base64'}, function (err) {
                        if (err) {
                            return console.log(err);
                        }
                    });
                }
            });
    }

    public static postLog(level: DebugLevel, message: string) {
        if (UmletEditorProvider.debugLevel !== undefined) {
            if (level.valueOf() <= UmletEditorProvider.debugLevel) {
                UmletEditorProvider.outputChannel.appendLine(message);
            }
        } else if (level.valueOf() === 0) { // If for some reason debugLevel = undefined, output standard logs.
            UmletEditorProvider.outputChannel.appendLine(message);
        }
    }

    public static getConfiguration(): WorkspaceConfiguration {
        return workspace.getConfiguration('umlet');
    }

    /*
    Overrides multiple vscode commands like copy and paste so they can be intercepted

    Select all for webviews is also intercepted so it can be disabled in umlet since it would select the property panel
    */
    public static overrideVsCodeCommands(context: vscode.ExtensionContext) {
        UmletEditorProvider.postLog(DebugLevel.STANDARD, "Overriding commands....");
        //COPY
        //override the editor.action.clipboardCopyAction with our own
        var clipboardCopyDisposable = vscode.commands.registerCommand('editor.action.clipboardCopyAction', overriddenClipboardCopyAction);
        context.subscriptions.push(clipboardCopyDisposable);

        /*
         * Function that overrides the default copy behavior. We get the selection and use it, dispose of this registered
         * command (returning to the default editor.action.clipboardCopyAction), invoke the default one, and then re-register it after the default completes
         */
        function overriddenClipboardCopyAction(textEditor: any, edit: any, params: any) {

            //debug
            //Write to output.
            UmletEditorProvider.postLog(DebugLevel.DETAILED, "Copy registered");
            //dispose of the overridden editor.action.clipboardCopyAction- back to default copy behavior
            clipboardCopyDisposable.dispose();


            //execute the default editor.action.clipboardCopyAction to copy
            vscode.commands.executeCommand("editor.action.clipboardCopyAction").then(function () {

                UmletEditorProvider.postLog(DebugLevel.DETAILED, "After Copy");
                if (currentlyActivePanel !== null) {
                    //if there is an actual panel in focus, just copy it
                    currentlyActivePanel?.webview.postMessage({command: 'copy'});
                } else if (lastCurrentlyActivePanelPurified() !== null) {
                    //else use command on the last active UMLet tab. this is to enable this command via context menu, as umlet looses focus when the edit menu is pressed on the now standard custom toolbar
                    lastCurrentlyActivePanelPurified()?.webview.postMessage({command: 'copy'});
                }

                //add the overridden editor.action.clipboardCopyAction back
                clipboardCopyDisposable = vscode.commands.registerCommand('editor.action.clipboardCopyAction', overriddenClipboardCopyAction);

                context.subscriptions.push(clipboardCopyDisposable);
            });
        }

        //PASTE
        //override the editor.action.clipboardPasteAction with our own
        var clipboardPasteDisposable = vscode.commands.registerCommand('editor.action.clipboardPasteAction', overriddenClipboardPasteAction);
        context.subscriptions.push(clipboardPasteDisposable);

        /*
         * Function that overrides the default paste behavior. We get the selection and use it, dispose of this registered
         * command (returning to the default editor.action.clipboardPasteAction), invoke the default one, and then re-register it after the default completes
         */
        function overriddenClipboardPasteAction(textEditor: any, edit: any, params: any) {

            //debug
            //Write to output.
            UmletEditorProvider.postLog(DebugLevel.DETAILED, "Paste registered");
            let panel = currentlyActivePanel ? currentlyActivePanel : lastCurrentlyActivePanelPurified();

            if (!panel || !panel?.visible) {
                UmletEditorProvider.postLog(DebugLevel.DETAILED, "Paste executed outside of UMLet");
                //dispose of the overridden editor.action.clipboardPasteAction- back to default paste behavior
                clipboardPasteDisposable.dispose();
                vscode.commands.executeCommand("editor.action.clipboardPasteAction").then(function () {
                    //add the overridden editor.action.clipboardPasteAction back
                    clipboardPasteDisposable = vscode.commands.registerCommand('editor.action.clipboardPasteAction', overriddenClipboardPasteAction);
                    context.subscriptions.push(clipboardPasteDisposable);
                });
            } else {
                if (UmletEditorProvider.isPropertyPanelFocus) {
                    //dispose of the overridden editor.action.clipboardPasteAction- back to default paste behavior
                    clipboardPasteDisposable.dispose();
                    // use command on the last active UMLet tab. this is to enable this command via context menu, as umlet looses focus when the edit menu is pressed on the now standard custom toolbar
                    vscode.commands.executeCommand("editor.action.clipboardPasteAction").then(function () {
                        //TODO remove UMLET tag from paste if needed
                        vscode.env.clipboard.readText().then((text) => {
                            let clipboard_content = text;
                            UmletEditorProvider.postLog(DebugLevel.DETAILED, "MESSAGE Paste, content is:" + clipboard_content);
                            panel?.webview.postMessage({
                                command: 'paste',
                                text: clipboard_content
                            });
                        });
                        UmletEditorProvider.postLog(DebugLevel.DETAILED, "After paste into properties panel");
                        //add the overridden editor.action.clipboardPasteAction back
                        clipboardPasteDisposable = vscode.commands.registerCommand('editor.action.clipboardPasteAction', overriddenClipboardPasteAction);
                        context.subscriptions.push(clipboardPasteDisposable);
                    });
                } else {
                    if (panel) {
                        // There exists a target panel and property panel is not in focus
                        vscode.env.clipboard.readText().then((text) => {
                            let clipboard_content = text;
                            UmletEditorProvider.postLog(DebugLevel.DETAILED, "MESSAGE Paste, content is:" + clipboard_content);
                            panel?.webview.postMessage({
                                command: 'paste',
                                text: clipboard_content
                            });
                        });
                        UmletEditorProvider.postLog(DebugLevel.DETAILED, "After paste of UML element");
                    }
                }
            }
        }

        //CUT
        //override the editor.action.clipboardCutDisposable with our own
        var clipboardCutDisposable = vscode.commands.registerCommand('editor.action.clipboardCutAction', overriddenClipboardCutAction);
        context.subscriptions.push(clipboardCutDisposable);

        /*
         * Function that overrides the default paste behavior. We get the selection and use it, dispose of this registered
         * command (returning to the default editor.action.clipboardPasteAction), invoke the default one, and then re-register it after the default completes
         */
        function overriddenClipboardCutAction(textEditor: any, edit: any, params: any) {

            //debug
            //Write to output.
            UmletEditorProvider.postLog(DebugLevel.DETAILED, "Cut registered");
            //dispose of the overridden editor.action.clipboardCutAction- back to default cut behavior
            clipboardCutDisposable.dispose();


            //execute the default editor.action.clipboardCutAction to cut
            vscode.commands.executeCommand("editor.action.clipboardCutAction").then(function () {

                UmletEditorProvider.postLog(DebugLevel.DETAILED, "After Cut");
                if (currentlyActivePanel !== null) {
                    UmletEditorProvider.postLog(DebugLevel.DETAILED, "MESSAGE Cut");
                    currentlyActivePanel?.webview.postMessage({command: 'cut'});
                } else if (lastCurrentlyActivePanelPurified() !== null) {
                    UmletEditorProvider.postLog(DebugLevel.DETAILED, "MESSAGE Cut");
                    lastCurrentlyActivePanelPurified()?.webview.postMessage({command: 'cut'});
                }
                //add the overridden editor.action.clipboardCutAction back
                clipboardCutDisposable = vscode.commands.registerCommand('editor.action.clipboardCutAction', overriddenClipboardCutAction);


                context.subscriptions.push(clipboardCutDisposable);
            });
        }

        //SELECT ALL
        //overwrite so that strg+a does not select any text in vs code
        var selectAllDisposable = vscode.commands.registerCommand('editor.action.webvieweditor.selectAll', overriddenSelectAllAction);
        context.subscriptions.push(selectAllDisposable);

        /*
         * Function that overrides the default select all behavior. We get the selection and use it, dispose of this registered
         * command (returning to the default editor.action.webvieweditor.selectAll), invoke the default one, and then re-register it after the default completes
         */
        function overriddenSelectAllAction(textEditor: any, edit: any, params: any) {
            //dispose of the overridden editor.action.clipboardCutAction- back to default cut behavior
            selectAllDisposable.dispose();

            //execute the default editor.action.webvieweditor.selectAll to cut
            if (!currentlyActivePanel) {
                vscode.commands.executeCommand("editor.action.webvieweditor.selectAll");
            }

            //add the overridden editor.action.webvieweditor.selectAll back
            selectAllDisposable = vscode.commands.registerCommand('editor.action.webvieweditor.selectAll', overriddenSelectAllAction);
            //set the subscription again so vscode can clean it up properly
            context.subscriptions.push(selectAllDisposable);
        }

        //ZOOM IN
        //overwrite so that strg+"+" does not zoom in whole editor during UMLet use
        var zoomInDisposable = vscode.commands.registerCommand('workbench.action.zoomIn', overriddenZoomInAction);
        context.subscriptions.push(zoomInDisposable);

        function overriddenZoomInAction() {
            if (!currentlyActivePanel || !currentlyActivePanel.visible) {
                zoomInDisposable.dispose();
                UmletEditorProvider.postLog(DebugLevel.DETAILED, 'Zooming in outside');
                vscode.commands.executeCommand('workbench.action.zoomIn').then(function () {
                    zoomInDisposable = vscode.commands.registerCommand('workbench.action.zoomIn', overriddenZoomInAction);
                    context.subscriptions.push(zoomInDisposable);
                });
            }
        }

        //ZOOM OUT
        //overwrite so that strg+"-" does not zoom out whole editor during UMLet use
        var zoomOutDisposable = vscode.commands.registerCommand('workbench.action.zoomOut', overriddenZoomOutAction);
        context.subscriptions.push(zoomOutDisposable);

        function overriddenZoomOutAction() {
            if (!currentlyActivePanel || !currentlyActivePanel.visible) {
                zoomOutDisposable.dispose();
                UmletEditorProvider.postLog(DebugLevel.DETAILED, 'Zooming out outside');
                vscode.commands.executeCommand('workbench.action.zoomOut').then(function () {
                    zoomOutDisposable = vscode.commands.registerCommand('workbench.action.zoomOut', overriddenZoomOutAction);
                    context.subscriptions.push(zoomOutDisposable);
                });
            }
        }
    }

    /**
     *
     * Gets a modified version of the initial starting page of the GWT umletino version
     * @param localUmletFolder folder which holds the local umletino gwt version.
     * @param diagramData XML data of a diagram which should be loaded on start
     * @param themeSetting preference which theme should be used
     */
    getUmletWebviewPage(localUmletFolder: string, diagramData: string, themeSetting: string) {
        let encodedDiagramData = encodeURIComponent(diagramData); //encode diagramData to prevent special characters to escape the string quotes which could lead to arbitrary javascript or html
        return `<!DOCTYPE html>
  <html>
    <head>
      <base href="${localUmletFolder}/" />
      <meta name="viewport" content="user-scalable=no" />
      <meta http-equiv="content-type" content="text/html; charset=UTF-8">
      <link type="text/css" rel="stylesheet" href="umletino.css">
      <link rel="icon" type="image/x-icon" href="favicon.ico">
      <title>UMLetino - Free Online UML Tool for Fast UML Diagrams</title>
      <script type="text/javascript" src="umletvscode/umletvscode.nocache.js"></script>
    </head>
    <body>

    <input type="text" id="copypaste" style="opacity: 0"/>
      <!-- the following line is necessary for history support -->
      <iframe src="javascript:''" id="__gwt_historyFrame" tabIndex='-1' style="position:absolute;width:0;height:0;border:0"></iframe>
      
      <!-- the website will not work without JavaScript -->
      <noscript>
        <div style="width: 25em; position: absolute; left: 50%; margin-left: -11em; background-color: white; border: 1px solid red; padding: 4px; font-family: sans-serif">
          You must enable JavaScript to use this web application.
      </div>
      </noscript>
      <div align="left" id="featurewarning" style="color: red; font-family: sans-serif; font-weight:bold; font-size:1.2em"></div>
    </body>
    <script>
      var vscode = acquireVsCodeApi();
      
      var themeSetting = \`${themeSetting}\`;
      
      window.addEventListener('message', function (event) {
          const message = event.data;
          switch (message.command) {
              case "themeSetting":
                  themeSetting = message.text;
                  switchTheme();
                  break;
          }
       });

      function getTheme() {
        switch (themeSetting) {
          case 'VS Code setting':
            switch (document.body.className) {
              case 'vscode-light':
                return 'LIGHT'; 
              case 'vscode-dark':
              case 'vscode-high-contrast':
                return 'DARK';
              default:
                return 'LIGHT';
            }
          case 'Light theme':
            return 'LIGHT';
          case 'Dark theme':
            return 'DARK';
        }        
      }

      function getEditorBackgroundColor() {
          switch (themeSetting) {
              case 'VS Code setting':
                return getComputedStyle(document.body).getPropertyValue('--vscode-editor-background');
              case 'Light theme':
                return '#FFFFFF';
              case 'Dark theme':
                return '#000000';
            }
      }

      function switchBackgroundColor(color) {
        document.body.style.backgroundColor = color;
      }

      // Observing theme changes
      var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutationRecord) {
            switchTheme();
        });    
      });
      
      observer.observe(document.body, { attributes : true, attributeFilter : ['class'] });

      // Retrieving current theme
      var theme = getTheme();
      
      var backgroundColor = getEditorBackgroundColor();
      switchBackgroundColor(backgroundColor);

      var vsCodeInitialDiagramData = \`${encodedDiagramData}\`;
      
      function switchTheme() {
        var themeFromClass = getTheme();
        backgroundColor = getEditorBackgroundColor();
        if (window.changeTheme) {
          window.changeTheme(themeFromClass, backgroundColor);
        }
        switchBackgroundColor(backgroundColor);
      }

      function notifyFocusVsCode() {
        vscode.postMessage({
          command: 'onFocus'
        })
      }

      function notifyBlurVsCode() {
        vscode.postMessage({
          command: 'onBlur'
        })
      }
      
    </script>
  </html>`;
    }
}
