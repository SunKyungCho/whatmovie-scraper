package me.toybox.whatmovie_data_shipper.dto;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;


@Getter @Setter
public class Company {
    private String companyCd;
    private String companyNm;
}