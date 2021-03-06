/*
 * Copyright 2017 trivago N.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trivago.rta.runners;

import com.trivago.rta.exceptions.CucablePluginException;
import com.trivago.rta.files.FileIO;
import com.trivago.rta.vo.FeatureRunner;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class RunnerFileContentRenderer {
    private static final String FEATURE_FILE_NAME_PLACEHOLDERs = "[FEATURE_FILE_NAME]";
    private static final String CUCABLE_FEATURE_PLACEHOLDER = "[CUCABLE:FEATURE]";
    private static final String CUCABLE_RUNNER_PLACEHOLDER = "[CUCABLE:RUNNER]";

    private final FileIO fileIO;

    @Inject
    public RunnerFileContentRenderer(FileIO fileIO) {
        this.fileIO = fileIO;
    }

    /**
     * Returns the full content for the concrete runner file.
     *
     * @param featureRunner The instance of the {@link FeatureRunner}.
     * @return The file content for the runner file.
     * @throws CucablePluginException see {@link CucablePluginException}.
     */
    public String getRenderedRunnerFileContent(
            FeatureRunner featureRunner
    ) throws CucablePluginException {

        final String runnerTemplatePath = featureRunner.getRunnerTemplatePath();
        final String runnerClassName = featureRunner.getRunnerClassName();

        String fileString = fileIO.readContentFromFile(runnerTemplatePath);
        if (runnerTemplatePath.endsWith(".java")) {
            fileString = replaceJavaTemplatePlaceholders(runnerTemplatePath, runnerClassName, fileString);
        }

        if (fileString.contains(FEATURE_FILE_NAME_PLACEHOLDERs)) {
            throw new CucablePluginException("The " + FEATURE_FILE_NAME_PLACEHOLDERs +
                    " placeholder is deprecated. Please use " + CUCABLE_FEATURE_PLACEHOLDER +
                    " or " + CUCABLE_RUNNER_PLACEHOLDER + " accordingly.");
        }

        fileString = replaceFeatureFilePlaceholder(fileString, featureRunner.getFeatureFileNames());
        fileString = fileString.replace(CUCABLE_RUNNER_PLACEHOLDER, runnerClassName);
        fileString = addCucableInfo(fileString, runnerTemplatePath);
        return fileString;
    }

    /**
     * Replace the feature placeholder in the template by the generated feature file names.
     *
     * @param runnerFileContentString The source string.
     * @param featureFileNames        The lost of feature file names.
     * @return The new string with the replaced feature placeholder.
     * @throws CucablePluginException see {@link CucablePluginException}.
     */
    private String replaceFeatureFilePlaceholder(
            final String runnerFileContentString,
            final List<String> featureFileNames) throws CucablePluginException {

        final String regex = "(\".*\\" + CUCABLE_FEATURE_PLACEHOLDER + ").*\"";
        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(runnerFileContentString);
        if (!matcher.find()) {
            throw new CucablePluginException("At least one " + CUCABLE_FEATURE_PLACEHOLDER + " placeholder is needed in your template.");
        }

        String fullCucableFeaturePlaceholder = matcher.group(0);
        StringBuilder completeFeatureStringBuilder = new StringBuilder();
        for (int i = 0; i < featureFileNames.size(); i++) {
            String featureFileName = featureFileNames.get(i);
            completeFeatureStringBuilder.append(
                    fullCucableFeaturePlaceholder.replace(CUCABLE_FEATURE_PLACEHOLDER, featureFileName)
            );
            if (i < featureFileNames.size() - 1) {
                completeFeatureStringBuilder.append(",\n");
            }
        }

        return runnerFileContentString.replace(fullCucableFeaturePlaceholder, completeFeatureStringBuilder);
    }

    /**
     * Adds the source scenario comments to the runner file content string.
     *
     * @param runnerFileContentString The source string.
     * @param runnerTemplatePath      The path to the runner template file.
     * @return The new string with the scenario information attached.
     */
    private String addCucableInfo(
            final String runnerFileContentString,
            final String runnerTemplatePath
    ) {
        return runnerFileContentString
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat("// Generated by Cucable from ")
                .concat(runnerTemplatePath.replace("\\", "/"))
                .concat(System.lineSeparator());
    }

    /**
     * Perform additional substitutions when the provided template is a java class file.
     *
     * @param runnerTemplatePath The path to the runner java template.
     * @param runnerClassName    The name of the runner file class.
     * @param fileString         The file content string of the rendered runner file.
     * @return The file content for the runner file with replaced package and java class name.
     */
    private String replaceJavaTemplatePlaceholders(
            final String runnerTemplatePath,
            final String runnerClassName,
            final String fileString
    ) {
        final String javaFileName = Paths.get(runnerTemplatePath).getFileName().toString();
        final String javaFileNameWithoutExtension = javaFileName.substring(0, javaFileName.lastIndexOf('.'));
        String replacedFileString = fileString.replace(javaFileNameWithoutExtension, runnerClassName);
        replacedFileString = replacedFileString.replaceAll("package .*;", "");
        return replacedFileString;
    }
}
