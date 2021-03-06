import { Injectable } from '@angular/core';
import { Http } from '@angular/http';
import 'rxjs/add/operator/toPromise';

import { RulesReport, ActivityReport } from '../models/index';

@Injectable()
export class ReportService {
  private readonly baseUrl = 'api/v1';

  constructor(public http: Http) { }

  getRulesReport(solrIndexId: string): Promise<RulesReport> {
    return this.http
      .get(this.baseUrl + '/report/rules-report/' + solrIndexId)
      .toPromise()
      .then(res => {
        // TODO convert backend date to readable format: yyyy-MM-dd HH:mm:ss
        return res.json() as RulesReport;
      })
  }

  getActivityReport(solrIndexId: string, dateFrom: string, dateTo: string): Promise<ActivityReport> {
    return this.http
      .get(this.baseUrl + '/report/activity-report/' + solrIndexId, { params: {
        dateFrom: dateFrom,
        dateTo: dateTo
      }})
      .toPromise()
      .then(res => {
        return res.json() as ActivityReport;
      })
  }
}
