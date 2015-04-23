package org.sharedhealth.datasense.scheduler.controller;

import org.quartz.SchedulerException;
import org.sharedhealth.datasense.scheduler.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping(value = "/scheduler")
public class SchedulerController {
    private SchedulerService schedulerService;

    @Autowired
    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @RequestMapping(value = "/manage", method = RequestMethod.GET)
    public ModelAndView manageScheduler() throws SchedulerException {
        ModelAndView modelAndView = new ModelAndView("index");
        modelAndView.addObject("runningJobs", schedulerService.getRunningJobs());
        modelAndView.addObject("stoppedJobs", schedulerService.getStoppedJobs());
        return modelAndView;
    }

    @RequestMapping(value = "/start", method = RequestMethod.GET)
    @ResponseBody
    public String startScheduler(
            @RequestParam(value = "reportId") Integer reportID,
            @RequestParam(value = "expression") String cronExpression,
            @RequestParam(value = "paramKey") String reportParamKey,
            @RequestParam(value = "paramValue") String reportParamValue)
            throws SchedulerException {

        return schedulerService.startJob(reportID, cronExpression, reportParamKey, reportParamValue);
    }

    @RequestMapping(value = "/stop", method = RequestMethod.GET)
    @ResponseBody
    public String stopScheduler(@RequestParam(value = "reportId") Integer reportID) throws SchedulerException {
        return schedulerService.stopJob(reportID);
    }


}
