import { InputTag } from './tags.model';

export enum ListItemType {
  RuleManagement, Spelling
}

export class ListItem {
  id: string;
  term: string;
  itemType: ListItemType;
  isActive: boolean;
  synonyms: Array<string>;
  tags: Array<InputTag>;
  comment: string;
  additionalTermsForSearch: Array<string>;
}
