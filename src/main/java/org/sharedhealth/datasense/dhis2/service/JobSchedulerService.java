package org.sharedhealth.datasense.dhis2.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.sharedhealth.datasense.dhis2.controller.ReportScheduleRequest;
import org.sharedhealth.datasense.dhis2.model.DHISOrgUnitConfig;
import org.sharedhealth.datasense.dhis2.model.DHISReportConfig;
import org.sharedhealth.datasense.dhis2.model.DatasetJobSchedule;
import org.sharedhealth.datasense.dhis2.repository.DHISConfigDao;
import org.sharedhealth.datasense.export.dhis.Jobs.DHISQuartzJob;
import org.sharedhealth.datasense.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.*;
import java.util.Calendar;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Service
public class JobSchedulerService {
    private static final Logger logger = Logger.getLogger(JobSchedulerService.class);
    private Scheduler scheduler;
    private DHISConfigDao dhisMapDao;


    @Autowired
    public JobSchedulerService(Scheduler scheduler, DHISConfigDao dhisMapDao) {
        this.scheduler = scheduler;
        this.dhisMapDao = dhisMapDao;
    }

    public void scheduleJob(ReportScheduleRequest scheduleRequest) throws SchedulerException {
        scheduleRequest(scheduleRequest);
    }

    private void scheduleRequest(ReportScheduleRequest scheduleRequest) {
        String datasetId = scheduleRequest.getDatasetId();
        DHISReportConfig configForDataset = dhisMapDao.getReportConfig(scheduleRequest.getConfigId());

        for (String facilityId : scheduleRequest.getSelectedFacilities()) {
            JobDetailFactoryBean jobFactory = new JobDetailFactoryBean();
            jobFactory.setJobClass(DHISQuartzJob.class);
            DHISOrgUnitConfig orgUnitConfig = dhisMapDao.findOrgUnitConfigFor(facilityId);

            String jobName = getJobName(scheduleRequest.getConfigId(), datasetId, facilityId, scheduleRequest.reportPeriod().period());
            jobFactory.setName(jobName);
            jobFactory.setGroup(datasetId);
            jobFactory.afterPropertiesSet();

            JobDetail jobDetail = jobFactory.getObject();
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            createJobMap(jobDataMap, scheduleRequest, scheduleRequest.getDatasetId(), configForDataset, facilityId, orgUnitConfig);

            String datasetName = scheduleRequest.getDatasetName();

            String triggerName = jobName + "-TRIGGER";
            Trigger trigger = getTrigger(scheduleRequest, datasetName, triggerName);
            try {
                if (!scheduler.checkExists(trigger.getKey())) {
                    scheduler.scheduleJob(jobDetail, trigger);
                } else {
                    logger.debug(String.format("A schedule already exists for this period, report and facility configuration."));
                    throw new RuntimeException("A schedule already exists for this period, report and facility configuration.");
                }
            } catch (SchedulerException e) {
                logger.debug("Could not schedule job. SchedulerException thrown.", e);
                throw new RuntimeException("Could not schedule job. error : " + e.getMessage(), e);
            }
            logger.info(String.format("Job %s starred", jobName));

        }
    }

    private Trigger getTrigger(ReportScheduleRequest scheduleRequest, String datasetName, String triggerName) {
        if (scheduleRequest.getScheduleType().equalsIgnoreCase("once")) {
            return newTrigger()
                    .withIdentity(triggerName, datasetName)
                    .startAt(afterSecs(30))
                    .build();
        } else {
            TriggerBuilder<CronTrigger> triggerBuilder = newTrigger()
                    .withIdentity(triggerName, datasetName)
                    .withSchedule(cronSchedule(scheduleRequest.getCronExp()));
            if(StringUtils.isNotBlank(scheduleRequest.getScheduleStartDate())) {
                Date triggerStartTime = null;
                try {
                    triggerStartTime = DateUtil.parseDate(scheduleRequest.getScheduleStartDate(), DateUtil.DATE_FMT_DD_MM_YYYY);
                } catch (ParseException e) {
                    throw new RuntimeException("invalid date:" + scheduleRequest.getScheduleStartDate());
                }
                triggerBuilder.startAt(triggerStartTime);
            }
            return triggerBuilder.build();
        }
    }

    private void createJobMap(Map dataMap, ReportScheduleRequest scheduleRequest, String datasetId, DHISReportConfig configForDataset, String facilityId, DHISOrgUnitConfig orgUnitConfig) {
        dataMap.put("paramStartDate", scheduleRequest.reportPeriod().startDate());
        dataMap.put("paramEndDate", scheduleRequest.reportPeriod().endDate());
        dataMap.put("paramPeriodType", scheduleRequest.getPeriodType());
        dataMap.put("paramFacilityId", facilityId);
        dataMap.put("paramDatasetId", datasetId);
        dataMap.put("paramOrgUnitId", orgUnitConfig.getOrgUnitId());
        dataMap.put("paramConfigFile", configForDataset.getConfigFile());
        dataMap.put("paramReportingPeriod", scheduleRequest.reportPeriod().period());
    }

    private Date afterSecs(int seconds) {
        Date date = new Date();
        java.util.Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.SECOND, seconds);
        return c.getTime();
    }

    private String getJobName(Integer configId, String datasetId, String facilityId, String period) {
        return datasetId + "." + configId + "-" + facilityId + "-" + period;
    }


    private String identifyFacilityForJob(String jobName) {
        String[] parts = jobName.split("-");
        return parts[1];
    }

    private String identifyPeriodForJob(String jobName) {
        String[] parts = jobName.split("-");
        return parts[2];
    }

    public List<DatasetJobSchedule> findAllJobsForDatasetConfig(Integer configId) throws SchedulerException {
        DHISReportConfig reportConfig = dhisMapDao.getReportConfig(configId);
        List<DatasetJobSchedule> reportSchedules = new ArrayList<DatasetJobSchedule>();
        if (reportConfig == null) {
            return reportSchedules;
        }

        String datasetId = reportConfig.getDatasetId();

        List<String> jobGroupNames = scheduler.getJobGroupNames();
        for (String groupName : jobGroupNames) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                String jobName = jobKey.getName();
                String matcher = datasetId + "." + reportConfig.getId() + "-";
                if (!jobName.startsWith(matcher)) {
                    continue;
                }

                String facilityId = identifyFacilityForJob(jobName);
                String periodForJob = identifyPeriodForJob(jobName);

                String jobGroup = jobKey.getGroup();
                //get job's trigger
                List<Trigger> triggers = (List<Trigger>) scheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : triggers) {
                    DatasetJobSchedule reportSchedule = new DatasetJobSchedule();
                    System.out.println("[jobName] : " + jobName + " [groupName] : " + jobGroup + " - " + trigger.getNextFireTime());
                    reportSchedule.setDatasetName(reportConfig.getDatasetName());
                    reportSchedule.setFacilityId(facilityId);
                    reportSchedule.setPeriod(periodForJob);
                    reportSchedule.setNextFireTime(toNotNullDateString(trigger.getNextFireTime()));
                    reportSchedule.setPrevFireTime(toNotNullDateString(trigger.getPreviousFireTime()));
                    reportSchedule.setStartTime(toNotNullDateString(trigger.getStartTime()));
                    reportSchedule.setEndTime(toNotNullDateString(trigger.getEndTime()));
                    reportSchedules.add(reportSchedule);

                }
            }

        }

        return reportSchedules;
    }

    private String toNotNullDateString(Date date) {
        return (date != null) ? date.toString() : "";
    }
}
